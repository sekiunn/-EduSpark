package com.eduspark.eduspark.service.impl;

import java.util.Set;

/**
 * Shared constants for supported interactive templates.
 */
public final class InteractiveTemplateCatalog {

    public static final String RENDER_MODE_TEMPLATE = "template";
    public static final String RENDER_MODE_CUSTOM_HTML = "custom_html";

    public static final String TYPE_ANIMATION_DEMO = "animation_demo";
    public static final String TYPE_PARAMETER_SIMULATION = "parameter_simulation";
    public static final String TYPE_QUIZ_PRACTICE = "quiz_practice";
    public static final String TYPE_DRAG_MATCH = "drag_match";
    public static final String TYPE_HOTSPOT_EXPLORE = "hotspot_explore";

    public static final String TEMPLATE_PHYSICS_COLLISION_V1 = "physics_collision_v1";
    public static final String TEMPLATE_PHYSICS_PROJECTILE_V1 = "physics_projectile_v1";
    public static final String TEMPLATE_PPT_DEMO_V1 = "ppt_demo_v1";
    public static final String TEMPLATE_FLOW_DEMO_V1 = "flow_demo_v1";
    public static final String TEMPLATE_ANIMATION_STEPPER_V1 = "animation_stepper_v1";
    public static final String TEMPLATE_PARAMETER_EXPLORER_V1 = "parameter_explorer_v1";
    public static final String TEMPLATE_QUIZ_PRACTICE_V1 = "quiz_practice_v1";
    public static final String TEMPLATE_DRAG_MATCH_V1 = "drag_match_v1";
    public static final String TEMPLATE_HOTSPOT_EXPLORE_V1 = "hotspot_explore_v1";

    public static final String TEMPLATE_VERSION_V1 = "1.0";

    public static final Set<String> SUPPORTED_TEMPLATE_IDS = Set.of(
            TEMPLATE_PHYSICS_COLLISION_V1,
            TEMPLATE_PHYSICS_PROJECTILE_V1,
            TEMPLATE_PPT_DEMO_V1,
            TEMPLATE_FLOW_DEMO_V1,
            TEMPLATE_ANIMATION_STEPPER_V1,
            TEMPLATE_PARAMETER_EXPLORER_V1,
            TEMPLATE_QUIZ_PRACTICE_V1,
            TEMPLATE_DRAG_MATCH_V1,
            TEMPLATE_HOTSPOT_EXPLORE_V1
    );

    private InteractiveTemplateCatalog() {
    }
}
