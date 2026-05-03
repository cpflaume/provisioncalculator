package com.provisions.calculator.api

import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class SwaggerAuthController {

    @GetMapping("/api/swagger-auth", produces = [MediaType.TEXT_HTML_VALUE])
    @ResponseBody
    fun swaggerAuth(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>API Docs</title>
  <link rel="stylesheet" type="text/css" href="/swagger-ui/swagger-ui.css">
  <link rel="icon" type="image/png" href="/swagger-ui/favicon-32x32.png" sizes="32x32"/>
  <style>html, body { margin: 0; background: #fafafa; }</style>
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="/swagger-ui/swagger-ui-bundle.js" charset="UTF-8"></script>
  <script src="/swagger-ui/swagger-ui-standalone-preset.js" charset="UTF-8"></script>
  <script>
    window.onload = function () {
      var hashParams = new URLSearchParams(window.location.hash.slice(1));
      var token = hashParams.get('token');
      if (token) {
        history.replaceState(null, '', window.location.pathname);
      }
      var ui = SwaggerUIBundle({
        url: '/v3/api-docs',
        dom_id: '#swagger-ui',
        deepLinking: true,
        presets: [
          SwaggerUIBundle.presets.apis,
          SwaggerUIStandalonePreset
        ],
        plugins: [SwaggerUIBundle.plugins.DownloadUrl],
        layout: 'StandaloneLayout',
        persistAuthorization: true,
        onComplete: function () {
          if (token) {
            ui.preauthorizeApiKey('bearerAuth', token);
          }
        }
      });
      window.ui = ui;
    };
  </script>
</body>
</html>
""".trimIndent()
}
