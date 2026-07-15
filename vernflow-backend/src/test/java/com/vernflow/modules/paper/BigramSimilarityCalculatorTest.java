package com.vernflow.modules.paper;

import com.vernflow.modules.paper.controller.*;
import com.vernflow.modules.paper.domain.*;
import com.vernflow.modules.paper.dto.*;
import com.vernflow.modules.paper.mapper.*;
import com.vernflow.modules.paper.repository.*;
import com.vernflow.modules.paper.service.*;
import com.vernflow.modules.paper.worker.*;


import com.vernflow.common.util.BigramSimilarityCalculator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BigramSimilarityCalculatorTest {

    private final BigramSimilarityCalculator calculator = new BigramSimilarityCalculator();

    @Test
    void shouldReturnFullScoreForSameTitle() {
        assertThat(calculator.calculate("Attention Is All You Need", "Attention Is All You Need"))
                .isEqualTo(1.0);
    }

    @Test
    void shouldReturnHigherScoreForRelatedTitles() {
        double related_score = calculator.calculate(
                "Knowledge Tracing with Transformer Models",
                "A Transformer-Based Model for Knowledge Tracing"
        );
        double unrelated_score = calculator.calculate(
                "Knowledge Tracing with Transformer Models",
                "Image Segmentation on Medical Scans"
        );

        assertThat(related_score).isGreaterThan(unrelated_score);
        assertThat(related_score).isGreaterThan(0.45);
    }
}

