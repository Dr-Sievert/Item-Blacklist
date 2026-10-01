package net.sievert.item_blacklist.neoforge.gametest;

import net.sievert.item_blacklist.gametest.AnnotationEra;
import net.sievert.item_blacklist.gametest.ItemBlacklistGameTests;
import java.io.File;
import javax.xml.parsers.ParserConfigurationException;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.neoforged.bus.api.IEventBus;

/**
 * The annotations era on NeoForge (up to 1.21.4): the scenarios go into vanilla's
 * GameTestRegistry while the mod loads. NeoForge's patched server Main starts vanilla's
 * GameTestServer on that registry when -Dneoforge.gameTestServer=true is set, in dev and in
 * production alike; RegisterGameTestsEvent is not used because it fires only when
 * GameTestHooks.isGametestEnabled(), never in production. Vanilla has no --report option in
 * this era, so the test mod installs the JUnit reporter itself when the system property
 * item_blacklist_gametest.report-file names a file.
 */
public final class NeoForgeGameTestsUntil1_21_5 implements GameTestRegistration.Backend {
    static final String REPORT_PROPERTY = ItemBlacklistGameTests.NAMESPACE + ".report-file";

    @Override
    public void register(IEventBus modBus) {
        AnnotationEra.register();
        String report = System.getProperty(REPORT_PROPERTY);
        if (report != null) {
            File file = new File(report).getAbsoluteFile();
            // Vanilla's JUnitLikeTestReporter does not create the folder.
            file.getParentFile().mkdirs();
            try {
                GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(file));
            } catch (ParserConfigurationException e) {
                throw new IllegalStateException("Cannot create the GameTest report " + file, e);
            }
        }
    }
}
