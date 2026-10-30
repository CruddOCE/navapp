package com.example.navapp.car;

import androidx.annotation.NonNull;
import androidx.car.app.model.CarIcon;
import androidx.core.graphics.drawable.IconCompat;
import com.example.navapp.NavLocationService;
import com.example.navapp.R;
import com.example.navapp.nav.TurnTypes;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Distance;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.NavigationManager;
import androidx.car.app.navigation.NavigationManagerCallback;
import androidx.car.app.navigation.model.Maneuver;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.navigation.model.RoutingInfo;
import androidx.car.app.navigation.model.Step;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

// The car screen. It shows the phone's next instruction and distance using
// Google's navigation template, which is the only layout a navigation app may
// show while driving. Without a route it shows a message instead.
public class NavScreen extends Screen implements NavState.Listener {

    public NavScreen(@NonNull CarContext carContext) {
        super(carContext);
        NavState.addListener(this);
        // Navigation must have a callback before navigationStarted() can be called,
        // or Android throws "No callback has been set" and the app crashes.
        carContext.getCarService(NavigationManager.class).setNavigationManagerCallback(
                ContextCompat.getMainExecutor(carContext),
                new NavigationManagerCallback() {
                    @Override
                    public void onStopNavigation() {
                        NavState.update(false, "", 0);
                        NavLocationService.stop(carContext);
                    }
                });
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                NavState.removeListener(NavScreen.this);
            }
        });
    }

    @Override
    public void onChanged() {
        invalidate();
    }

    // Whether the car has been told navigation is running, so it is told once at
    // the start and once at the end (audit item: navigationEnded was never sent).
    private boolean navigating = false;

    @NonNull
    @Override
    public Template onGetTemplate() {
        NavigationManager manager = getCarContext().getCarService(NavigationManager.class);
        if (!NavState.isActive()) {
            if (navigating) {
                manager.navigationEnded();
                navigating = false;
            }
            return new MessageTemplate.Builder("Start a route on your phone to see directions here.")
                    .setTitle("navapp")
                    .setHeaderAction(Action.APP_ICON)
                    .build();
        }

        if (!navigating) {
            manager.navigationStarted();
            navigating = true;
        }

        // The host shows no turn card for an UNKNOWN manoeuvre without an icon, so
        // every step carries a real type (TurnTypes) and an arrow.
        int type = NavState.getTurnType() == TurnTypes.UNKNOWN ? TurnTypes.STRAIGHT : NavState.getTurnType();
        Maneuver.Builder maneuver = new Maneuver.Builder(type)
                .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), iconFor(type))).build());
        if (TurnTypes.needsExitNumber(type)) maneuver.setRoundaboutExitNumber(Math.max(1, NavState.getExitNumber()));
        Step step = new Step.Builder(NavState.getInstruction())
                .setManeuver(maneuver.build())
                .build();
        RoutingInfo routing = new RoutingInfo.Builder()
                .setCurrentStep(step, Distance.create(NavState.getDistanceM(), Distance.UNIT_METERS))
                .build();

        // The navigation template will not build without an action strip (the car
        // app crashed with "Action strip for this template must be set" as soon as
        // a route started). End stops the location service, which clears the car
        // state here and tells the phone's page the route has ended.
        ActionStrip actions = new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("End")
                        .setOnClickListener(() -> NavLocationService.stop(getCarContext()))
                        .build())
                .build();

        return new NavigationTemplate.Builder()
                .setNavigationInfo(routing)
                .setActionStrip(actions)
                .build();
    }

    // One arrow per kind of turn; keep, fork and ramp turns share the slight and
    // normal arrows, which is what they look like from the driver's seat.
    static int iconFor(int type) {
        switch (type) {
            case TurnTypes.TURN_NORMAL_RIGHT:
            case TurnTypes.ON_RAMP_NORMAL_RIGHT:
            case TurnTypes.OFF_RAMP_NORMAL_RIGHT:
            case TurnTypes.DESTINATION_RIGHT:
                return R.drawable.turn_right;
            case TurnTypes.TURN_NORMAL_LEFT:
            case TurnTypes.ON_RAMP_NORMAL_LEFT:
            case TurnTypes.OFF_RAMP_NORMAL_LEFT:
            case TurnTypes.DESTINATION_LEFT:
                return R.drawable.turn_left;
            case TurnTypes.TURN_SLIGHT_RIGHT:
            case TurnTypes.KEEP_RIGHT:
            case TurnTypes.FORK_RIGHT:
            case TurnTypes.ON_RAMP_SLIGHT_RIGHT:
            case TurnTypes.OFF_RAMP_SLIGHT_RIGHT:
                return R.drawable.turn_slight_right;
            case TurnTypes.TURN_SLIGHT_LEFT:
            case TurnTypes.KEEP_LEFT:
            case TurnTypes.FORK_LEFT:
            case TurnTypes.ON_RAMP_SLIGHT_LEFT:
            case TurnTypes.OFF_RAMP_SLIGHT_LEFT:
                return R.drawable.turn_slight_left;
            case TurnTypes.TURN_SHARP_RIGHT:
            case TurnTypes.ON_RAMP_SHARP_RIGHT:
                return R.drawable.turn_sharp_right;
            case TurnTypes.TURN_SHARP_LEFT:
            case TurnTypes.ON_RAMP_SHARP_LEFT:
                return R.drawable.turn_sharp_left;
            case TurnTypes.U_TURN_RIGHT:
                return R.drawable.turn_uturn_right;
            case TurnTypes.U_TURN_LEFT:
                return R.drawable.turn_uturn_left;
            case TurnTypes.MERGE_LEFT:
            case TurnTypes.MERGE_RIGHT:
            case TurnTypes.MERGE_SIDE_UNSPECIFIED:
                return R.drawable.turn_merge;
            case TurnTypes.ROUNDABOUT_ENTER_AND_EXIT_CW:
            case TurnTypes.ROUNDABOUT_ENTER_AND_EXIT_CCW:
            case TurnTypes.ROUNDABOUT_EXIT_CW:
            case TurnTypes.ROUNDABOUT_EXIT_CCW:
                return R.drawable.turn_roundabout;
            case TurnTypes.DEPART:
                return R.drawable.turn_depart;
            case TurnTypes.DESTINATION:
            case TurnTypes.DESTINATION_STRAIGHT:
                return R.drawable.turn_destination;
            default:
                return R.drawable.turn_straight;
        }
    }
}
