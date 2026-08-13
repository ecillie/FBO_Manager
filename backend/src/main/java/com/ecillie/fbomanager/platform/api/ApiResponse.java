package com.ecillie.fbomanager.platform.api;

public record ApiResponse<T>(T data, ApiMeta meta) {
}
