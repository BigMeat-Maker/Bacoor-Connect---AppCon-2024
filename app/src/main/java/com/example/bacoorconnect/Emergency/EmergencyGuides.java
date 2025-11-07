package com.example.bacoorconnect.Emergency;

import static androidx.browser.customtabs.CustomTabsClient.getPackageName;
import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.widget.MediaController;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.VideoView;

import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.example.bacoorconnect.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class EmergencyGuides extends Fragment {

    private View rootView;
    private ImageView earthquakeB, earthquakeD, earthquakeA, floodB, floodD, floodA,
            firstaidSteps, firstaidBleed, firstaidFract, firstaidMBurn, firstaidSBurn, firstaidConv, firstaidHA, firstaidStr,
            landslideB, landslideD, landslideA, fireSafety, fireExt, fireD, fireA;
    private FloatingActionButton scrolltotopBtn;
    private NestedScrollView nestedScrollView;
    private VideoView earthquakeVideo, fireVideo, landslideVideo;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_emergency_guides, container, false);

        scrolltotopBtn = rootView.findViewById(R.id.scrollToTopBtn);
        nestedScrollView = rootView.findViewById(R.id.nestedScrollView);



        // ref
        earthquakeB = rootView.findViewById(R.id.earthquake_before);
        earthquakeD = rootView.findViewById(R.id.earthquake_during);
        earthquakeA = rootView.findViewById(R.id.earthquake_after);
        floodB = rootView.findViewById(R.id.flood_before);
        floodD = rootView.findViewById(R.id.flood_during);
        floodA = rootView.findViewById(R.id.flood_after);
        firstaidSteps = rootView.findViewById(R.id.firstaid_foursteps);
        firstaidBleed = rootView.findViewById(R.id.firstaid_bleeding);
        firstaidFract = rootView.findViewById(R.id.firstaid_fracture);
        firstaidMBurn = rootView.findViewById(R.id.firstaid_minorburn);
        firstaidSBurn = rootView.findViewById(R.id.firstaid_severeburn);
        firstaidConv = rootView.findViewById(R.id.firstaid_convulsion);
        firstaidHA = rootView.findViewById(R.id.firstaid_heartattack);
        firstaidStr = rootView.findViewById(R.id.firstaid_stroke);
        landslideB = rootView.findViewById(R.id.landslide_before);
        landslideD = rootView.findViewById(R.id.landslide_during);
        landslideA = rootView.findViewById(R.id.landslide_after);
        fireSafety = rootView.findViewById(R.id.fire_safety);
        fireExt = rootView.findViewById(R.id.fire_extinguisher);
        fireD = rootView.findViewById(R.id.fire_during);
        fireA = rootView.findViewById(R.id.fire_after);
        earthquakeVideo = rootView.findViewById(R.id.EarthquakeVideoView);
        fireVideo = rootView.findViewById(R.id.FireVideoView);
        landslideVideo = rootView.findViewById(R.id.LandslideVideoView);


        // animation
        setupAnimatedCard(R.id.earthquake_header, R.id.earthquake_content);
        setupAnimatedCard(R.id.flood_header, R.id.flood_content);
        setupAnimatedCard(R.id.firstaid_header, R.id.firstaid_content);
        setupAnimatedCard(R.id.landslide_header, R.id.landslide_content);
        setupAnimatedCard(R.id.fire_header, R.id.fire_content);


        // video views
        Uri earthquakeUri = Uri.parse("android.resource://" + requireContext().getPackageName() + "/" + R.raw.earthquake_disaster_preparedness);
        Uri fireUri = Uri.parse("android.resource://" + requireContext().getPackageName() + "/" + R.raw.fire_disaster_preparedness);
        Uri landslideUri = Uri.parse("android.resource://" + requireContext().getPackageName() + "/" + R.raw.landslide_disaster_preparedness);

        earthquakeVideo.setVideoURI(earthquakeUri);
        fireVideo.setVideoURI(fireUri);
        landslideVideo.setVideoURI(landslideUri);


        MediaController controller1 = new MediaController(requireContext());
        controller1.setAnchorView(earthquakeVideo);
        controller1.setMediaPlayer(earthquakeVideo);
        earthquakeVideo.setMediaController(controller1);

        MediaController controller2 = new MediaController(requireContext());
        controller2.setAnchorView(fireVideo);
        controller2.setMediaPlayer(fireVideo);
        fireVideo.setMediaController(controller2);

        MediaController controller3 = new MediaController(requireContext());
        controller3.setAnchorView(landslideVideo);
        controller3.setMediaPlayer(landslideVideo);
        landslideVideo.setMediaController(controller3);

        // autostarts btw
        earthquakeVideo.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            earthquakeVideo.start();
        });

        fireVideo.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            fireVideo.start();
        });

        landslideVideo.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            landslideVideo.start();
        });


        // infographics
        View.OnClickListener zoomClickListener = v -> {
        ImageView clickedImage = (ImageView) v;
        showZoomDialog(clickedImage.getDrawable());
        };

        scrolltotopBtn.setOnClickListener(v -> {
            nestedScrollView.smoothScrollTo(0,0);
        });

        nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY > 600) {
                        scrolltotopBtn.show();
                    } else {
                        scrolltotopBtn.hide();
                    }
                }
        );

        nestedScrollView.post(() -> {
            if (nestedScrollView.getScrollY() > 600) {
                scrolltotopBtn.show();
            } else {
                scrolltotopBtn.hide();
            }
        });

        // infographics + video zoom
        earthquakeB.setOnClickListener(zoomClickListener);
        earthquakeD.setOnClickListener(zoomClickListener);
        earthquakeA.setOnClickListener(zoomClickListener);
        floodB.setOnClickListener(zoomClickListener);
        floodD.setOnClickListener(zoomClickListener);
        floodA.setOnClickListener(zoomClickListener);
        firstaidSteps.setOnClickListener(zoomClickListener);
        firstaidBleed.setOnClickListener(zoomClickListener);
        firstaidFract.setOnClickListener(zoomClickListener);
        firstaidMBurn.setOnClickListener(zoomClickListener);
        firstaidSBurn.setOnClickListener(zoomClickListener);
        firstaidConv.setOnClickListener(zoomClickListener);
        firstaidHA.setOnClickListener(zoomClickListener);
        firstaidStr.setOnClickListener(zoomClickListener);
        landslideB.setOnClickListener(zoomClickListener);
        landslideD.setOnClickListener(zoomClickListener);
        landslideA.setOnClickListener(zoomClickListener);
        fireSafety.setOnClickListener(zoomClickListener);
        fireExt.setOnClickListener(zoomClickListener);
        fireD.setOnClickListener(zoomClickListener);
        fireA.setOnClickListener(zoomClickListener);

        return rootView;
    }

    private void showZoomDialog(Drawable imageDrawable) {
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.fragment_zoom_image); // double-check this layout name

        ImageView zoomedImage = dialog.findViewById(R.id.zoomedImage);
        ImageButton btnClose = dialog.findViewById(R.id.btnClose);

        zoomedImage.setImageDrawable(imageDrawable);

        Animation fadeIn = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in);
        Animation fadeOut = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out);

        zoomedImage.startAnimation(fadeIn);
        btnClose.startAnimation(fadeIn);

        btnClose.setOnClickListener(view -> {
            zoomedImage.startAnimation(fadeOut);
            btnClose.startAnimation(fadeOut);

            new Handler(Looper.getMainLooper()).postDelayed(dialog::dismiss, 200);
        });

        dialog.show();
    }


    private void setupAnimatedCard(int headerId, int contentId) {
        FrameLayout header = rootView.findViewById(headerId);
        LinearLayout content = rootView.findViewById(contentId);

        header.setOnClickListener(v -> toggleContentWithAnimation(content));
    }

    private void toggleContentWithAnimation(View content) {
        if (content.getVisibility() == View.VISIBLE) {
            // Collapse with animation
            Animation collapse = AnimationUtils.loadAnimation(getContext(), R.anim.collapse);
            collapse.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationRepeat(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    content.setVisibility(View.GONE);
                }
            });
            content.startAnimation(collapse);
        } else {
            // Expand with animation
            content.setVisibility(View.VISIBLE);
            Animation expand = AnimationUtils.loadAnimation(getContext(), R.anim.expand);
            content.startAnimation(expand);
        }
    }

}