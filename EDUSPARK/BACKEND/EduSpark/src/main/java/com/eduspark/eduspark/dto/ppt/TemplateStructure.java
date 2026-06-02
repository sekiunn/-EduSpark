package com.eduspark.eduspark.dto.ppt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateStructure implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int totalSlides;

    private int repeatableSlideIndex;

    private List<TemplateSlideStructure> slides;
}
