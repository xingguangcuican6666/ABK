#include <winsock2.h>
#include <ws2tcpip.h>

#include "utils.h"

#include <flutter_windows.h>
#include <io.h>
#include <stdio.h>
#include <windows.h>

#include <filesystem>
#include <algorithm>
#include <iostream>
#include <optional>

namespace {

constexpr wchar_t kSidecarHost[] = L"127.0.0.1";
constexpr unsigned short kSidecarPort = 38765;
constexpr DWORD kSidecarStartupTimeoutMs = 15000;
constexpr wchar_t kSidecarExeName[] = L"abk_sidecar.exe";
constexpr wchar_t kPythonExeRelative[] = L"runtime\\python\\python.exe";
constexpr wchar_t kAppRootSentinel1[] = L"cli\\abk.py";
constexpr wchar_t kAppRootSentinel2[] = L"hmbird_patch.c";

std::filesystem::path GetExecutablePath() {
  wchar_t buffer[MAX_PATH];
  const DWORD length = ::GetModuleFileNameW(nullptr, buffer, MAX_PATH);
  return std::filesystem::path(std::wstring(buffer, length));
}

bool FileExists(const std::filesystem::path& path) {
  std::error_code error;
  return std::filesystem::is_regular_file(path, error);
}

bool DirectoryExists(const std::filesystem::path& path) {
  std::error_code error;
  return std::filesystem::is_directory(path, error);
}

std::optional<std::filesystem::path> ResolveExistingFile(
    const std::filesystem::path& base_dir,
    const std::vector<std::filesystem::path>& candidates) {
  for (const auto& relative : candidates) {
    const auto candidate = std::filesystem::weakly_canonical(base_dir / relative);
    if (FileExists(candidate)) {
      return candidate;
    }
  }
  return std::nullopt;
}

bool LooksLikeAppRoot(const std::filesystem::path& path) {
  return DirectoryExists(path) && FileExists(path / kAppRootSentinel1) &&
         FileExists(path / kAppRootSentinel2);
}

std::optional<std::filesystem::path> ResolveAppRoot(
    const std::filesystem::path& exe_dir) {
  for (const auto& relative : {
           std::filesystem::path(L"resources\\abk"),
           std::filesystem::path(L"..\\resources\\abk"),
           std::filesystem::path(L"..\\..\\..\\..\\..\\..\\.."),
       }) {
    const auto candidate = std::filesystem::weakly_canonical(exe_dir / relative);
    if (LooksLikeAppRoot(candidate)) {
      return candidate;
    }
  }
  return std::nullopt;
}

std::optional<std::filesystem::path> ResolvePythonPath(
    const std::filesystem::path& exe_dir) {
  for (const auto& relative : {
           std::filesystem::path(kPythonExeRelative),
           std::filesystem::path(L"..\\") / kPythonExeRelative,
       }) {
    const auto candidate = std::filesystem::weakly_canonical(exe_dir / relative);
    if (FileExists(candidate)) {
      return candidate;
    }
  }
  return std::nullopt;
}

bool WaitForSidecarHealth(unsigned short port, DWORD timeout_ms) {
  WSADATA wsa_data;
  if (WSAStartup(MAKEWORD(2, 2), &wsa_data) != 0) {
    return false;
  }

  const DWORD deadline = ::GetTickCount() + timeout_ms;
  while (::GetTickCount() < deadline) {
    SOCKET socket_handle = ::socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (socket_handle != INVALID_SOCKET) {
      sockaddr_in address{};
      address.sin_family = AF_INET;
      address.sin_port = htons(port);
      ::InetPtonW(AF_INET, kSidecarHost, &address.sin_addr);
      if (::connect(socket_handle, reinterpret_cast<sockaddr*>(&address),
                    sizeof(address)) == 0) {
        static constexpr char kHealthRequest[] =
            "GET /api/v1/health HTTP/1.1\r\n"
            "Host: 127.0.0.1\r\n"
            "Connection: close\r\n\r\n";
        const int sent = ::send(socket_handle, kHealthRequest,
                                static_cast<int>(sizeof(kHealthRequest) - 1), 0);
        if (sent > 0) {
          char buffer[1024];
          const int received = ::recv(socket_handle, buffer, sizeof(buffer) - 1, 0);
          if (received > 0) {
            buffer[received] = '\0';
            const std::string response(buffer);
            if (response.find("200 OK") != std::string::npos ||
                response.find("\"status\":\"ok\"") != std::string::npos) {
              ::closesocket(socket_handle);
              WSACleanup();
              return true;
            }
          }
        }
      }
      ::closesocket(socket_handle);
    }
    ::Sleep(250);
  }

  WSACleanup();
  return false;
}

std::wstring Utf16Path(const std::filesystem::path& path) {
  return path.wstring();
}

}  // namespace

void CreateAndAttachConsole() {
  if (::AllocConsole()) {
    FILE *unused;
    if (freopen_s(&unused, "CONOUT$", "w", stdout)) {
      _dup2(_fileno(stdout), 1);
    }
    if (freopen_s(&unused, "CONOUT$", "w", stderr)) {
      _dup2(_fileno(stdout), 2);
    }
    std::ios::sync_with_stdio();
    FlutterDesktopResyncOutputStreams();
  }
}

std::vector<std::string> GetCommandLineArguments() {
  // Convert the UTF-16 command line arguments to UTF-8 for the Engine to use.
  int argc;
  wchar_t** argv = ::CommandLineToArgvW(::GetCommandLineW(), &argc);
  if (argv == nullptr) {
    return std::vector<std::string>();
  }

  std::vector<std::string> command_line_arguments;

  // Skip the first argument as it's the binary name.
  for (int i = 1; i < argc; i++) {
    command_line_arguments.push_back(Utf8FromUtf16(argv[i]));
  }

  ::LocalFree(argv);

  if (::GetEnvironmentVariableW(L"ABK_DESKTOP_BASE_URL", nullptr, 0) > 0) {
    wchar_t buffer[512];
    const DWORD length =
        ::GetEnvironmentVariableW(L"ABK_DESKTOP_BASE_URL", buffer, 512);
    if (length > 0) {
      const std::string base_url = Utf8FromUtf16(buffer);
      const bool already_set = std::find(command_line_arguments.begin(),
                                         command_line_arguments.end(),
                                         "--abk-base-url") !=
                               command_line_arguments.end();
      if (!already_set && !base_url.empty()) {
        command_line_arguments.push_back("--abk-base-url");
        command_line_arguments.push_back(base_url);
      }
    }
  }

  return command_line_arguments;
}

std::string Utf8FromUtf16(const wchar_t* utf16_string) {
  if (utf16_string == nullptr) {
    return std::string();
  }
  // First, find the length of the string with a safe upper bound (CWE-126).
  // UNICODE_STRING_MAX_CHARS (32767) is the maximum length of a UNICODE_STRING.
  int input_length = static_cast<int>(wcsnlen(utf16_string, UNICODE_STRING_MAX_CHARS));
  // Now use that bounded length to determine the required buffer size.
  // When an explicit length is passed, WideCharToMultiByte does not include
  // the null terminator in its returned size.
  int target_length = ::WideCharToMultiByte(
      CP_UTF8, WC_ERR_INVALID_CHARS, utf16_string,
      input_length, nullptr, 0, nullptr, nullptr);
  std::string utf8_string;
  if (target_length == 0 || static_cast<size_t>(target_length) > utf8_string.max_size()) {
    return utf8_string;
  }
  utf8_string.resize(target_length);
  int converted_length = ::WideCharToMultiByte(
      CP_UTF8, WC_ERR_INVALID_CHARS, utf16_string,
      input_length, utf8_string.data(), target_length, nullptr, nullptr);
  if (converted_length == 0) {
    return std::string();
  }
  return utf8_string;
}

SidecarProcess::~SidecarProcess() {
  Stop();
}

bool SidecarProcess::EnsureRunning(std::wstring* error_message) {
  ::SetEnvironmentVariableW(L"ABK_DESKTOP_BASE_URL",
                            L"http://127.0.0.1:38765");

  if (WaitForSidecarHealth(kSidecarPort, 500)) {
    return true;
  }

  const auto exe_path = GetExecutablePath();
  const auto exe_dir = exe_path.parent_path();
  const auto sidecar_path = ResolveExistingFile(
      exe_dir, {std::filesystem::path(kSidecarExeName),
                std::filesystem::path(L"..\\") / kSidecarExeName});
  if (!sidecar_path.has_value()) {
    if (error_message != nullptr) {
      *error_message = L"Missing abk_sidecar.exe next to the Windows desktop bundle.";
    }
    return false;
  }

  const auto app_root = ResolveAppRoot(exe_dir);
  if (!app_root.has_value()) {
    if (error_message != nullptr) {
      *error_message = L"Failed to locate ABK runtime resources (resources\\\\abk).";
    }
    return false;
  }

  ::SetEnvironmentVariableW(L"ABK_DESKTOP_BASE_URL",
                            L"http://127.0.0.1:38765");
  ::SetEnvironmentVariableW(L"ABK_DESKTOP_APP_ROOT",
                            Utf16Path(*app_root).c_str());
  if (const auto python_path = ResolvePythonPath(exe_dir); python_path.has_value()) {
    ::SetEnvironmentVariableW(L"ABK_DESKTOP_PYTHON",
                              Utf16Path(*python_path).c_str());
  }

  std::wstring command_line =
      L"\"" + Utf16Path(*sidecar_path) + L"\" --port 38765";
  STARTUPINFOW startup_info{};
  startup_info.cb = sizeof(startup_info);
  ZeroMemory(&process_info_, sizeof(process_info_));

  if (!::CreateProcessW(
          nullptr, command_line.data(), nullptr, nullptr, FALSE,
          CREATE_NO_WINDOW, nullptr, exe_dir.c_str(), &startup_info,
          &process_info_)) {
    if (error_message != nullptr) {
      *error_message = L"Failed to start abk_sidecar.exe.";
    }
    return false;
  }
  started_ = true;

  if (!WaitForSidecarHealth(kSidecarPort, kSidecarStartupTimeoutMs)) {
    Stop();
    if (error_message != nullptr) {
      *error_message =
          L"abk_sidecar.exe did not become healthy on http://127.0.0.1:38765/api/v1/health.";
    }
    return false;
  }

  return true;
}

void SidecarProcess::Stop() {
  if (!started_) {
    return;
  }
  if (process_info_.hProcess != nullptr) {
    ::TerminateProcess(process_info_.hProcess, 0);
    ::CloseHandle(process_info_.hProcess);
    process_info_.hProcess = nullptr;
  }
  if (process_info_.hThread != nullptr) {
    ::CloseHandle(process_info_.hThread);
    process_info_.hThread = nullptr;
  }
  started_ = false;
}
