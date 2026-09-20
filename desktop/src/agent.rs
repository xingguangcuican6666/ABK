use anyhow::{anyhow, Context, Result};
use axum::http::HeaderMap;
use reqwest::Method;
use serde_json::Value;
use std::time::Duration;

#[derive(Debug, Clone)]
pub struct RemoteAgentClient {
    http: reqwest::Client,
}

impl RemoteAgentClient {
    pub fn new() -> Result<Self> {
        let http = reqwest::Client::builder()
            .timeout(Duration::from_secs(30))
            .build()
            .context("failed to build reqwest client")?;
        Ok(Self { http })
    }

    pub async fn get_json(&self, base_url: &str, path: &str) -> Result<Value> {
        let response = self
            .request(base_url, Method::GET, path, &HeaderMap::new(), None)
            .await?;
        let status = response.status();
        let text = response
            .text()
            .await
            .context("failed to read response body")?;
        if !status.is_success() {
            return Err(anyhow!("HTTP {}: {}", status, text.trim()));
        }
        serde_json::from_str(&text).context("failed to parse JSON")
    }

    pub async fn post_json(
        &self,
        base_url: &str,
        path: &str,
        body: &Value,
    ) -> Result<reqwest::Response> {
        let url = format!("{base_url}{path}");
        self.http
            .post(url)
            .json(body)
            .send()
            .await
            .context("failed to send JSON request")
    }

    pub async fn request(
        &self,
        base_url: &str,
        method: Method,
        path: &str,
        headers: &HeaderMap,
        body: Option<Vec<u8>>,
    ) -> Result<reqwest::Response> {
        let url = format!("{base_url}{path}");
        let mut request = self.http.request(method, url);
        for (name, value) in headers {
            request = request.header(name, value);
        }
        if let Some(body) = body {
            request = request.body(body);
        }
        request.send().await.context("failed to proxy request")
    }
}
