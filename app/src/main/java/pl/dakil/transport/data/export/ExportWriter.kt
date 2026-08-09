package pl.dakil.transport.data.export

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.time.OffsetDateTime
import pl.dakil.transport.domain.model.ExportFormat
import pl.dakil.transport.domain.model.ExportSettings
import pl.dakil.transport.domain.model.Journey

/**
 * Writes [journey] to [out] in [format], and closes nothing — the caller owns the stream.
 *
 * The stream is bytes rather than characters because KMZ is a zip: the text writers wrap it
 * themselves, each in the UTF-8 its header promises.
 */
fun writeExport(
    out: OutputStream,
    format: ExportFormat,
    journey: Journey,
    settings: ExportSettings,
    labels: ExportLabels,
    now: OffsetDateTime = OffsetDateTime.now(),
) {
    when (format) {
        ExportFormat.KMZ -> writeKmz(out, journey, settings, labels, now)
        ExportFormat.GPX -> out.textWriter { writeGpx(it, journey, settings, labels, now) }
        ExportFormat.GEOJSON -> out.textWriter { writeGeoJson(it, journey, settings, labels, now) }
    }
}

/**
 * Runs [block] against a UTF-8 writer over this stream, flushed but not closed. Closing here would
 * close the stream underneath it, which is the caller's to do (and, for a cache file, must happen
 * after the uri is handed out).
 */
private inline fun OutputStream.textWriter(block: (Writer) -> Unit) {
    val writer = OutputStreamWriter(this, Charsets.UTF_8).buffered()
    block(writer)
    writer.flush()
}
