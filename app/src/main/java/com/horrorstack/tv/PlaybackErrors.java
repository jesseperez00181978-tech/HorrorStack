package com.horrorstack.tv;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;

/** Shared, credential-free error handling for both native player surfaces. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class PlaybackErrors {
    private PlaybackErrors() { }

    static int httpStatus(Throwable error) {
        for (int depth = 0; error != null && depth < 16; depth++, error = error.getCause()) {
            if (error instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) error).responseCode;
            }
        }
        return -1;
    }

    static boolean shouldTryAlternateEngine(PlaybackException error) {
        if (error.errorCode >= 3000 && error.errorCode < 5000) return true;
        // A different in-app HTTP stack can handle transport incompatibilities.
        // Do not retry explicit access denial, missing content or rate limits.
        int status = httpStatus(error);
        if (status == 401 || status == 403 || status == 404 || status == 410 || status == 429) return false;
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS;
    }

    static String describe(PlaybackException error) {
        int status = httpStatus(error);
        if (status != -1) {
            return "Stream request returned HTTP " + status
                    + ". Report this number and whether you used Wi-Fi or mobile data.";
        }
        return "Playback failed: " + error.getErrorCodeName() + ".";
    }
}
