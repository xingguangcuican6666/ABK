import 'package:abk_desktop/src/core/api/abk_sidecar_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

void main() {
  test('wraps broken pipe client exceptions as SidecarException', () async {
    final client = HttpAbkSidecarClient(
      baseUrl: 'http://127.0.0.1:38765',
      client: _BrokenPipeClient(),
    );

    expect(
      () => client.detectDevices(),
      throwsA(
        isA<SidecarException>()
            .having((error) => error.isNetwork, 'isNetwork', isTrue)
            .having(
              (error) => error.message,
              'message',
              contains('Unable to reach'),
            ),
      ),
    );
  });
}

class _BrokenPipeClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return Future<http.StreamedResponse>.error(
      http.ClientException('断开的管道', request.url),
    );
  }
}
