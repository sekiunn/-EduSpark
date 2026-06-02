package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;

import java.util.function.Consumer;

public interface IInteractiveHtmlWriterService {

    String streamInitialHtml(InteractiveContext context, Consumer<String> onChunk);

    String streamRefinedHtml(InteractiveContext context, String currentHtml, String instruction, Consumer<String> onChunk);
}
