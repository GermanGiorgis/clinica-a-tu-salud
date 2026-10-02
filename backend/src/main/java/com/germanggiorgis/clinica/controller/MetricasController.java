package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.MetricasResponse;
import com.germanggiorgis.clinica.service.MetricasService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metricas")
@RequiredArgsConstructor
public class MetricasController {

    private final MetricasService metricasService;

    @GetMapping
    public MetricasResponse obtener() {
        return metricasService.obtener();
    }
}
