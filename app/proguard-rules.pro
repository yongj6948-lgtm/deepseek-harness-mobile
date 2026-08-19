# The app exposes no JavaScript bridge. WebView callbacks are direct framework overrides.

# JSch tunnels (+ key management + SFTP) are loaded dynamically at runtime; keep them
# so SSH authentication isn't broken by release minification.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
