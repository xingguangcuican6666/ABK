use reqwest::Url;
use serde::{Deserialize, Serialize};
use std::env;
use std::net::IpAddr;

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ProxySettings {
    pub(crate) http_proxy: Option<String>,
    pub(crate) https_proxy: Option<String>,
    pub(crate) all_proxy: Option<String>,
    pub(crate) no_proxy: Option<String>,
}

impl ProxySettings {
    pub(crate) fn from_env() -> Self {
        Self {
            http_proxy: first_env_value(&["http_proxy", "HTTP_PROXY"]),
            https_proxy: first_env_value(&["https_proxy", "HTTPS_PROXY"]),
            all_proxy: first_env_value(&["all_proxy", "ALL_PROXY"]),
            no_proxy: first_env_value(&["no_proxy", "NO_PROXY"]),
        }
    }

    pub(crate) fn apply_to_process_env(&self) {
        apply_proxy_env_key("http_proxy", self.http_proxy.as_deref());
        apply_proxy_env_key("HTTP_PROXY", self.http_proxy.as_deref());
        apply_proxy_env_key("https_proxy", self.https_proxy.as_deref());
        apply_proxy_env_key("HTTPS_PROXY", self.https_proxy.as_deref());
        apply_proxy_env_key("all_proxy", self.all_proxy.as_deref());
        apply_proxy_env_key("ALL_PROXY", self.all_proxy.as_deref());
        apply_proxy_env_key("no_proxy", self.no_proxy.as_deref());
        apply_proxy_env_key("NO_PROXY", self.no_proxy.as_deref());
    }

    pub(crate) fn container_env_args(&self) -> Vec<String> {
        self.container_env_args_for_host(None)
    }

    pub(crate) fn requires_host_network_for_container(&self) -> bool {
        proxy_uses_loopback_host(self.http_proxy.as_deref())
            || proxy_uses_loopback_host(self.https_proxy.as_deref())
            || proxy_uses_loopback_host(self.all_proxy.as_deref())
    }

    pub(crate) fn container_env_args_for_host(&self, host_alias: Option<&str>) -> Vec<String> {
        let mut args = Vec::new();
        let http_proxy = rewrite_container_proxy_url(self.http_proxy.as_deref(), host_alias);
        let https_proxy = rewrite_container_proxy_url(self.https_proxy.as_deref(), host_alias);
        let all_proxy = rewrite_container_proxy_url(self.all_proxy.as_deref(), host_alias);
        push_env_arg(&mut args, "http_proxy", http_proxy.as_deref());
        push_env_arg(&mut args, "HTTP_PROXY", http_proxy.as_deref());
        push_env_arg(&mut args, "https_proxy", https_proxy.as_deref());
        push_env_arg(&mut args, "HTTPS_PROXY", https_proxy.as_deref());
        push_env_arg(&mut args, "all_proxy", all_proxy.as_deref());
        push_env_arg(&mut args, "ALL_PROXY", all_proxy.as_deref());
        push_env_arg(&mut args, "no_proxy", self.no_proxy.as_deref());
        push_env_arg(&mut args, "NO_PROXY", self.no_proxy.as_deref());
        args
    }
}

pub(crate) fn normalize_proxy_value(value: Option<String>) -> Option<String> {
    value
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
}

fn apply_proxy_env_key(key: &str, value: Option<&str>) {
    if let Some(value) = value {
        env::set_var(key, value);
    } else {
        env::remove_var(key);
    }
}

fn push_env_arg(args: &mut Vec<String>, key: &str, value: Option<&str>) {
    let Some(value) = value.map(str::trim).filter(|value| !value.is_empty()) else {
        return;
    };
    args.push("-e".into());
    args.push(format!("{key}={value}"));
}

fn rewrite_container_proxy_url(value: Option<&str>, host_alias: Option<&str>) -> Option<String> {
    let value = value.map(str::trim).filter(|value| !value.is_empty())?;
    let Some(host_alias) = host_alias else {
        return Some(value.to_string());
    };
    let Ok(mut url) = Url::parse(value) else {
        return Some(value.to_string());
    };
    if !is_loopback_host(url.host_str()) {
        return Some(value.to_string());
    }
    if url.set_host(Some(host_alias)).is_err() {
        return Some(value.to_string());
    }
    Some(url.to_string())
}

fn proxy_uses_loopback_host(value: Option<&str>) -> bool {
    let value = value.map(str::trim).filter(|value| !value.is_empty());
    let Some(value) = value else {
        return false;
    };
    let Ok(url) = Url::parse(value) else {
        return false;
    };
    is_loopback_host(url.host_str())
}

fn is_loopback_host(host: Option<&str>) -> bool {
    match host {
        Some(host) if host.eq_ignore_ascii_case("localhost") => true,
        Some(host) => host
            .parse::<IpAddr>()
            .map(|ip| ip.is_loopback())
            .unwrap_or(false),
        None => false,
    }
}

fn first_env_value(keys: &[&str]) -> Option<String> {
    keys.iter()
        .find_map(|key| normalize_proxy_value(env::var(key).ok()))
}

#[cfg(test)]
mod tests {
    use super::ProxySettings;

    #[test]
    fn container_env_args_follow_proxy_settings_shape() {
        let settings = ProxySettings {
            http_proxy: Some("http://proxy.example:7890".into()),
            https_proxy: Some("http://proxy.example:7890".into()),
            all_proxy: Some("socks5://proxy.example:7891".into()),
            no_proxy: Some("localhost,127.0.0.1".into()),
        };

        assert_eq!(
            settings.container_env_args(),
            vec![
                "-e",
                "http_proxy=http://proxy.example:7890",
                "-e",
                "HTTP_PROXY=http://proxy.example:7890",
                "-e",
                "https_proxy=http://proxy.example:7890",
                "-e",
                "HTTPS_PROXY=http://proxy.example:7890",
                "-e",
                "all_proxy=socks5://proxy.example:7891",
                "-e",
                "ALL_PROXY=socks5://proxy.example:7891",
                "-e",
                "no_proxy=localhost,127.0.0.1",
                "-e",
                "NO_PROXY=localhost,127.0.0.1",
            ]
        );
    }

    #[test]
    fn container_env_args_skip_missing_values() {
        let settings = ProxySettings {
            http_proxy: None,
            https_proxy: Some("https://proxy.example".into()),
            all_proxy: Some("socks5://proxy.example:1080".into()),
            no_proxy: None,
        };

        assert_eq!(
            settings.container_env_args(),
            vec![
                "-e",
                "https_proxy=https://proxy.example",
                "-e",
                "HTTPS_PROXY=https://proxy.example",
                "-e",
                "all_proxy=socks5://proxy.example:1080",
                "-e",
                "ALL_PROXY=socks5://proxy.example:1080",
            ]
        );
    }

    #[test]
    fn container_env_args_rewrite_loopback_proxy_for_container_host() {
        let settings = ProxySettings {
            http_proxy: Some("http://127.0.0.1:7890".into()),
            https_proxy: Some("http://localhost:7890".into()),
            all_proxy: Some("socks5://127.0.0.1:7891".into()),
            no_proxy: Some("localhost,127.0.0.1".into()),
        };

        assert_eq!(
            settings.container_env_args_for_host(Some("host.docker.internal")),
            vec![
                "-e",
                "http_proxy=http://host.docker.internal:7890/",
                "-e",
                "HTTP_PROXY=http://host.docker.internal:7890/",
                "-e",
                "https_proxy=http://host.docker.internal:7890/",
                "-e",
                "HTTPS_PROXY=http://host.docker.internal:7890/",
                "-e",
                "all_proxy=socks5://host.docker.internal:7891",
                "-e",
                "ALL_PROXY=socks5://host.docker.internal:7891",
                "-e",
                "no_proxy=localhost,127.0.0.1",
                "-e",
                "NO_PROXY=localhost,127.0.0.1",
            ]
        );
    }

    #[test]
    fn loopback_proxies_require_host_network_for_container() {
        let settings = ProxySettings {
            http_proxy: Some("http://127.0.0.1:7890".into()),
            https_proxy: None,
            all_proxy: None,
            no_proxy: None,
        };

        assert!(settings.requires_host_network_for_container());
    }

    #[test]
    fn non_loopback_proxies_do_not_require_host_network_for_container() {
        let settings = ProxySettings {
            http_proxy: Some("http://proxy.example:7890".into()),
            https_proxy: None,
            all_proxy: Some("socks5://proxy.example:7891".into()),
            no_proxy: None,
        };

        assert!(!settings.requires_host_network_for_container());
    }
}
