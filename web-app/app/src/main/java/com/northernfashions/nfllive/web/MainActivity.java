package com.northernfashions.nfllive.web;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.WindowManager;
import android.window.OnBackInvokedDispatcher;

/**
 * NFL LIVE app: opens the live web version. Every change made to the web version
 * reaches this app by itself, so the app never has to be downloaded again for an update.
 */
public class MainActivity extends Activity {

  private static final String HOME = "https://shamimusfba-design.github.io/NFL_LIVE/";
  private static final String HOST = "shamimusfba-design.github.io";
  private WebView web;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    boolean isTv = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    UiModeManager ui = (UiModeManager) getSystemService(UI_MODE_SERVICE);
    if (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) isTv = true;
    if (isTv) {
      // A wall display must not go to sleep.
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    web = new WebView(this);
    setContentView(web);

    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);        // keeps the sign-in and the Day/Night choice
    s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false); // the short alert sound can play
    s.setUseWideViewPort(true);
    s.setLoadWithOverviewMode(true);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

    CookieManager.getInstance().setAcceptCookie(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

    web.setWebChromeClient(new WebChromeClient());
    web.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri url = request.getUrl();
        if (HOST.equalsIgnoreCase(url.getHost())) return false; // our pages stay in the app
        try {
          startActivity(new Intent(Intent.ACTION_VIEW, url));  // anything else opens outside
        } catch (Exception ignored) { }
        return true;
      }

      @Override
      public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (request.isForMainFrame()) {
          String page = "<html><body style='font-family:sans-serif;background:#061B43;color:#fff;"
            + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center'>"
            + "<div><h2>No internet connection</h2><p>NFL LIVE needs the internet. Check Wi-Fi or mobile data.</p>"
            + "<button style='font-size:18px;padding:12px 28px;border:0;border-radius:12px;background:#1677FF;color:#fff' "
            + "onclick=\"location.href='" + HOME + "'\">Try again</button></div></body></html>";
          view.loadDataWithBaseURL(null, page, "text/html", "UTF-8", null);
        }
      }
    });

    if (savedInstanceState != null) {
      web.restoreState(savedInstanceState);
    } else {
      // A TV opens in Day mode the first time; after that the TV keeps its own choice.
      web.loadUrl(isTv ? HOME + "?defaultTheme=light" : HOME);
    }

    if (Build.VERSION.SDK_INT >= 33) {
      getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
        OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
    }
  }

  private void handleBack() {
    if (web != null && web.canGoBack()) web.goBack();
    else finish();
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onBackPressed() {
    handleBack();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (web != null) web.saveState(outState);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (web != null) web.onResume();
  }

  @Override
  protected void onPause() {
    if (web != null) web.onPause();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (web != null) web.destroy();
    super.onDestroy();
  }
}
