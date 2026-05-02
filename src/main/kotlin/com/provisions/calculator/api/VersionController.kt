package com.provisions.calculator.api

import io.swagger.v3.oas.annotations.Operation
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/version")
class VersionController(private val buildProperties: BuildProperties?) {

    @GetMapping
    @Operation(summary = "Returns the application version")
    fun getVersion(): VersionResponse =
        VersionResponse(version = buildProperties?.version ?: "dev")

    data class VersionResponse(val version: String)
}
