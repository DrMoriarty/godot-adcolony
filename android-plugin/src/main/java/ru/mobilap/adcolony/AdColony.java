package ru.mobilap.adcolony;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;

import org.godotengine.godot.Godot;
import org.godotengine.godot.GodotLib;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;

//import com.adcolony.sdk.AdColony;
import com.adcolony.sdk.AdColonyAdOptions;
import com.adcolony.sdk.AdColonyAdSize;
import com.adcolony.sdk.AdColonyAdView;
import com.adcolony.sdk.AdColonyAdViewListener;
import com.adcolony.sdk.AdColonyAppOptions;
import com.adcolony.sdk.AdColonyInterstitial;
import com.adcolony.sdk.AdColonyInterstitialListener;
import com.adcolony.sdk.AdColonyReward;
import com.adcolony.sdk.AdColonyRewardListener;
import com.adcolony.sdk.AdColonyUserMetadata;
import com.adcolony.sdk.AdColonyZone;

public class AdColony extends GodotPlugin 
{
    private final String TAG = AdColony.class.getName();

    private HashMap<String, View> zombieBanners = new HashMap<>();
    private HashMap<String, FrameLayout.LayoutParams> bannerParams = new HashMap<>();
    private HashMap<String, Integer> callbacks = new HashMap<>();
    private HashMap<String, AdColonyInterstitial> interstitials = new HashMap<>();
    private HashMap<String, AdColonyAdView> banners = new HashMap<>();
    private HashMap<String, AdColonyInterstitial> rewardeds = new HashMap<>();

    private boolean ProductionMode = true; // Store if is real or not

    private FrameLayout layout = null; // Store the layout
    private AdColonyAdOptions adOptions;

    /* Init
     * ********************************************************************** */

    /**
     * Prepare for work with YandexAds
     * @param boolean ProductionMode Tell if the enviroment is for real or test
     * @param int gdscript instance id
     */
    public void init(final String appId, final String zoneIds, boolean ProductionMode) {

        String[] zoneArray = zoneIds.split(",");
        
        this.ProductionMode = ProductionMode;
        layout = (FrameLayout)getActivity().getWindow().getDecorView().getRootView();

        AdColonyAppOptions appOptions = new AdColonyAppOptions()
            .setTestModeEnabled(!ProductionMode)
            .setGDPRRequired(false)
            .setKeepScreenOn(true);
        com.adcolony.sdk.AdColony.configure(getActivity(), appOptions, appId, zoneArray);

        /*
          AdColonyUserMetadata metadata = new AdColonyUserMetadata()
          .setUserAge(26)
          .setUserEducation(AdColonyUserMetadata.USER_EDUCATION_BACHELORS_DEGREE)
          .setUserGender(AdColonyUserMetadata.USER_MALE);
        */
        adOptions = new AdColonyAdOptions();
        //adOptions.setUserMetadata(metadata);
        //adOptions.enableConfirmationDialog(true);
        //adOptions.enableResultsDialog(true);

        // Create and set a reward listener
        com.adcolony.sdk.AdColony.setRewardListener(new AdColonyRewardListener() {
                @Override
                public void onReward(AdColonyReward reward) {
                    // Query reward object for info here
                    Log.d( TAG, "onReward" );
                    int callback_id = callbacks.get(reward.getZoneID());
                    if(reward.success()) {
                        GodotLib.calldeferred(callback_id, "_on_rewarded", new Object[] { reward.getZoneID(), reward.getRewardName(), reward.getRewardAmount() });
                    }
                }
            });
    }

    /* Rewarded Video
     * ********************************************************************** */
    private AdColonyInterstitialListener makeRewardedListener(final String id, final int callback_id)
    {
        return new AdColonyInterstitialListener() {
            @Override
            public void onRequestFilled(AdColonyInterstitial ad) {
                // Ad passed back in request filled callback, ad can now be shown
                Log.d(TAG, "Rewarded: onRequestFilled");
                rewardeds.put(id, ad);
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_ad_loaded", new Object[] { id });
            }
            @Override
            public void onRequestNotFilled(AdColonyZone zone) {
                // Ad request was not filled
                Log.d(TAG, "Rewarded: onRequestNotFilled");
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_ad_failed_to_load", new Object[] { id, "No ads" });
            }
            @Override
            public void onOpened(AdColonyInterstitial ad) {
                // Ad opened, reset UI to reflect state change
                Log.d(TAG, "Rewarded: onOpened");
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_started", new Object[] { id });
            }
            @Override
            public void onClosed(AdColonyInterstitial ad) {
                // Ad opened, reset UI to reflect state change
                Log.d(TAG, "Rewarded: onClosed");
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_ad_closed", new Object[] { id });
            }
            @Override
            public void onExpiring(AdColonyInterstitial ad) {
                // Request a new ad if ad is expiring
                Log.d(TAG, "Rewarded: onExpiring");
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_ad_failed_to_load", new Object[] { id, "Ad expiring" });
            }
        };
    }

    /**
     * Load a Rewarded Video
     * @param String id AdMod Rewarded video ID
     */
    public void loadRewardedVideo(final String id, final int callback_id) {
        getActivity().runOnUiThread(new Runnable() {
                @Override public void run() {
                    callbacks.put(id, callback_id);
                    com.adcolony.sdk.AdColony.requestInterstitial(id, makeRewardedListener(id, callback_id), adOptions);
                }
            });
    }

    /**
     * Show a Rewarded Video
     */
    public void showRewardedVideo(final String id) {
        getActivity().runOnUiThread(new Runnable() {
                @Override public void run() {
                    if(rewardeds.containsKey(id)) {
                        AdColonyInterstitial rewarded = rewardeds.get(id);
                        rewarded.show();
                    } else {
                        Log.w(TAG, "showRewardedVideo - rewarded not loaded");
                    }
                }
            });
    }

    /* Banner
     * ********************************************************************** */

    private AdColonyAdViewListener makeBannerListener(final String id, final boolean isOnTop, final int callback_id)
    {
        return new AdColonyAdViewListener() {
            @Override
            public void onRequestFilled(AdColonyAdView adColonyAdView) {
                Log.d(TAG, "Banner: onRequestFilled");
                banners.put(id, adColonyAdView);
                GodotLib.calldeferred(callback_id, "_on_banner_loaded", new Object[]{ id });

                FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                if(isOnTop) adParams.gravity = Gravity.TOP;
                else adParams.gravity = Gravity.BOTTOM;
                adColonyAdView.setBackgroundColor(/* Color.WHITE */Color.TRANSPARENT);
                bannerParams.put(id, adParams);
            }

            @Override
            public void onRequestNotFilled(AdColonyZone zone) {
                Log.d(TAG, "Banner: onRequestNotFilled");
                GodotLib.calldeferred(callback_id, "_on_banner_failed_to_load", new Object[]{ id, "No ads" });
            }

            @Override
            public void onOpened(AdColonyAdView ad) {
                Log.d(TAG, "Banner: onOpened");
            }

            @Override
            public void onClosed(AdColonyAdView ad) {
                Log.d(TAG, "Banner: onClosed");
            }

            @Override
            public void onClicked(AdColonyAdView ad) {
                Log.d(TAG, "Banner: onClicked");
            }

            @Override
            public void onLeftApplication(AdColonyAdView ad) {
                Log.d(TAG, "Banner: onLeftApplication");
            }
        };
    }


    private void placeBannerOnScreen(final String id, final AdColonyAdView banner)
    {
        FrameLayout.LayoutParams adParams = bannerParams.get(id);
        layout.addView(banner, adParams);
    }

    /**
     * Load a banner
     * @param String id AdMod Banner ID
     * @param boolean isOnTop To made the banner top or bottom
     */
    public void loadBanner(final String id, final boolean isOnTop, final int callback_id)
    {
        getActivity().runOnUiThread(new Runnable() {
                @Override public void run() {
                    if(!banners.containsKey(id)) {
                        com.adcolony.sdk.AdColony.requestAdView(id, makeBannerListener(id, isOnTop, callback_id), AdColonyAdSize.BANNER, adOptions);
                    } else {
                        Log.w(TAG, "Banner already loaded: " + id);
                    }
                }
            });
    }

    /**
     * Show the banner
     */
    public void showBanner(final String id)
    {
        getActivity().runOnUiThread(new Runnable() {
                @Override public void run() {
                    if(banners.containsKey(id)) {
                        AdColonyAdView b = banners.get(id);
                        if(b.getParent() == null) {
                            placeBannerOnScreen(id, b);
                        }
                        b.setVisibility(View.VISIBLE);
                        for (String key : banners.keySet()) {
                            if(!key.equals(id)) {
                                AdColonyAdView b2 = banners.get(key);
                                b2.setVisibility(View.GONE);
                            }
                        }
                        Log.d(TAG, "Show Banner");
                    } else {
                        Log.w(TAG, "Banner not found: "+id);
                    }
                }
            });
    }

    public void removeBanner(final String id)
    {
        getActivity().runOnUiThread(new Runnable() {
                @Override public void run() {
                    if(banners.containsKey(id)) {
                        AdColonyAdView b = banners.get(id);
                        banners.remove(id);
                        layout.removeView(b); // Remove the banner
                        b.destroy();
                        Log.d(TAG, "Remove Banner");
                    } else {
                        Log.w(TAG, "Banner not found: "+id);
                    }
                }
            });
    }

    /**
     * Hide the banner
     */
    public void hideBanner(final String id)
    {
        getActivity().runOnUiThread(new Runnable() {
                @Override public void run() {
                    if(banners.containsKey(id)) {
                        AdColonyAdView b = banners.get(id);
                        b.setVisibility(View.GONE);
                        Log.d(TAG, "Hide Banner");
                    } else {
                        Log.w(TAG, "Banner not found: "+id);
                    }
                }
            });
    }

    /**
     * Get the banner width
     * @return int Banner width
     */
    public int getBannerWidth(final String id)
    {
        if(banners.containsKey(id)) {
            AdColonyAdView b = banners.get(id);
            if(b != null) {
                int w = b.getWidth();
                if(w == 0) {
                    Resources r = getActivity().getResources();
                    w = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, b.getAdSize().getWidth(), r.getDisplayMetrics());
                }
                return w;
            } else return 0;
        } else return 0;
    }

    /**
     * Get the banner height
     * @return int Banner height
     */
    public int getBannerHeight(final String id)
    {
        if(banners.containsKey(id)) {
            AdColonyAdView b = banners.get(id);
            if(b != null) {
                int h = b.getHeight();
                if(h == 0) {
                    Resources r = getActivity().getResources();
                    h = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, b.getAdSize().getHeight(), r.getDisplayMetrics());
                }
                return h;
            } else
                return 0;
        } else {
            return 0;
        }
    }

    public String makeZombieBanner(final String id)
    {
        if (banners.containsKey(id)) {
            AdColonyAdView b = banners.get(id);
            String zid = java.util.UUID.randomUUID().toString();
            banners.remove(id);
            zombieBanners.put(zid, b);
            Log.i(TAG, "makeZombieBanner: OK");
            return zid;
        } else {
            Log.w(TAG, "makeZombieBanner: Banner not found: "+id);
            return "";
        }
    }

    public void killZombieBanner(final String zid)
    {
        getActivity().runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (zombieBanners.containsKey(zid)) {
                        View z = zombieBanners.get(zid);
                        zombieBanners.remove(zid);
                        layout.removeView(z); // Remove the zombie banner
                        Log.w(TAG, "killZombieBanner: OK");
                    } else {
                        Log.w(TAG, "killZombieBanner: Banner not found: "+zid);
                    }
                }
            });
    }

    /* Interstitial
     * ********************************************************************** */
    private AdColonyInterstitialListener makeInterstitialListener(final String id, final int callback_id)
    {
        return new AdColonyInterstitialListener() {
            @Override
            public void onRequestFilled(AdColonyInterstitial ad) {
                // Ad passed back in request filled callback, ad can now be shown
                Log.d(TAG, "Interstitial: onRequestFilled");
                interstitials.put(id, ad);
                GodotLib.calldeferred(callback_id, "_on_interstitial_loaded", new Object[] { id });
            }
            @Override
            public void onRequestNotFilled(AdColonyZone zone) {
                // Ad request was not filled
                Log.d(TAG, "Interstitial: onRequestNotFilled");
                GodotLib.calldeferred(callback_id, "_on_interstitial_failed_to_load", new Object[] { id, "No ads" });
            }
            @Override
            public void onOpened(AdColonyInterstitial ad) {
                // Ad opened, reset UI to reflect state change
                Log.d(TAG, "Interstitial: onOpened");
            }
            @Override
            public void onClosed(AdColonyInterstitial ad) {
                // Ad opened, reset UI to reflect state change
                Log.d(TAG, "Interstitial: onClosed");
                GodotLib.calldeferred(callback_id, "_on_interstitial_close", new Object[] { id });
            }
            @Override
            public void onExpiring(AdColonyInterstitial ad) {
                // Request a new ad if ad is expiring
                Log.d(TAG, "Interstitial: onExpiring");
                GodotLib.calldeferred(callback_id, "_on_interstitial_failed_to_load", new Object[] { id, "Ad expiring" });
            }
        };
    }


    /**
     * Load a interstitial
     * @param String id AdMod Interstitial ID
     */
    public void loadInterstitial(final String id, final int callback_id)
    {
        getActivity().runOnUiThread(new Runnable() {
                @Override public void run() {
                    // Load an ad for a given zone
                    com.adcolony.sdk.AdColony.requestInterstitial(id, makeInterstitialListener(id, callback_id), adOptions);
                }
            });
    }

    /**
     * Show the interstitial
     */
    public void showInterstitial(final String id)
    {
        getActivity().runOnUiThread(new Runnable() {
                @Override public void run() {
                    if(interstitials.containsKey(id)) {
                        AdColonyInterstitial interstitial = interstitials.get(id);
                        interstitial.show();
                    } else {
                        Log.w(TAG, "Interstitial not found: " + id);
                    }
                }
            });
    }

    /* Definitions
     * ********************************************************************** */
    public AdColony(Godot godot) 
    {
        super(godot);
    }

    @Override
    public String getPluginName() {
        return "AdColony";
    }

    @Override
    public List<String> getPluginMethods() {
        return Arrays.asList(
                             "init",
                             // banner
                             "loadBanner", "showBanner", "hideBanner", "removeBanner", "getBannerWidth", "getBannerHeight",
                             "makeZombieBanner", "killZombieBanner",
                             // Interstitial
                             "loadInterstitial", "showInterstitial",
                             // Rewarded video
                             "loadRewardedVideo", "showRewardedVideo"
                             );
    }

    /*
    @Override
    public Set<SignalInfo> getPluginSignals() {
        return Collections.singleton(loggedInSignal);
    }
    */

    @Override
    public View onMainCreate(Activity activity) {
        return null;
    }
}
