package com.eduspark.eduspark.dto.courseware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSlideFill implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int slideIndex;

    private boolean isDuplicate;

    private Map<String, String> markerValues;
}
