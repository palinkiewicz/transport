package pl.dakil.transport.data.export

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.transport.domain.model.ExportFileName
import pl.dakil.transport.domain.model.ExportFormat

/** The suggested filename is echoed back by share targets and document pickers, so it has to be
 * something every filesystem accepts — and to end in the extension of the format actually written.
 */
class ExportFileNameTest {

    private val journey = ExportFixtures.journey

    private fun name(
        mode: ExportFileName,
        format: ExportFormat = ExportFormat.GPX,
        from: String = "Home",
        to: String = "Airport",
    ) = exportFileName(journey, mode, format, from, to)

    @Test
    fun `file names are slugged and follow the chosen mode`() {
        assertEquals("home-airport-2026-08-06.gpx", name(ExportFileName.ROUTE_AND_DATE))
        assertEquals(
            "warszawa-centralna-lotnisko-chopina-2026-08-06.gpx",
            name(ExportFileName.ROUTE_AND_DATE, from = "Warszawa Centralna", to = "Lotnisko Chopina!"),
        )
        assertEquals("itinerary-20260806-0825.gpx", name(ExportFileName.DATE_TIME))
        assertEquals("itinerary.gpx", name(ExportFileName.PLAIN))
    }

    @Test
    fun `the extension is the chosen format's, in every naming mode`() {
        ExportFormat.entries.forEach { format ->
            ExportFileName.entries.forEach { mode ->
                assertEquals(
                    "${format.name} / ${mode.name}",
                    ".${format.extension}",
                    name(mode, format).substring(name(mode, format).lastIndexOf('.')),
                )
            }
        }
    }
}
