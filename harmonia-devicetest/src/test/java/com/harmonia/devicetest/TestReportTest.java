package com.harmonia.devicetest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestReportTest {

    @TempDir
    Path folder;

    @Test
    void statusFollowsChecksAndAnswers() {
        TestReport report = new TestReport(folder);
        TestReport.Entry ok = report.begin("T01", "ok", "");
        ok.check("réponse", true);
        ok.answer("version ?", Console.Answer.YES);
        assertEquals(TestReport.Status.PASSED, ok.status());

        TestReport.Entry unsure = report.begin("T02", "unsure", "");
        unsure.answer("?", Console.Answer.DONT_KNOW);
        assertEquals(TestReport.Status.INCONCLUSIVE, unsure.status());

        TestReport.Entry failed = report.begin("T03", "failed", "");
        failed.answer("?", Console.Answer.DONT_KNOW);
        failed.answer("parts ?", Console.Answer.NO);
        assertEquals(TestReport.Status.FAILED, failed.status());
        assertEquals(0, failed.failures());

        TestReport.Entry check = report.begin("T04", "check", "");
        check.check("checksum", false);
        assertEquals(1, check.failures());
        check.force(TestReport.Status.SKIPPED);
        assertEquals(TestReport.Status.SKIPPED, check.status());
    }

    @Test
    void markdownReportIsWritten() throws IOException {
        TestReport report = new TestReport(folder);
        report.context("Canal du SR", "1");
        TestReport.Entry e = report.begin("T05", "Banque", "Lecture d'une banque");
        e.check("ré-encodage identique", true);
        e.block("Performances :\n  00 Ski Jam");
        e.file("T05-patches-RAM1.syx", "banque de patchs");
        report.write();

        String md = Files.readString(report.file());
        assertTrue(md.contains("| Canal du SR | 1 |"));
        assertTrue(md.contains("| T05 | Banque |"));
        assertTrue(md.contains("00 Ski Jam"));
        assertTrue(md.contains("[T05-patches-RAM1.syx](T05-patches-RAM1.syx)"));
    }

    @Test
    void everyTestHasAUniqueId() {
        long ids = DeviceTestRunner.allTests().stream().map(DeviceTest::id).distinct().count();
        assertEquals(DeviceTestRunner.allTests().size(), ids);
    }
}
