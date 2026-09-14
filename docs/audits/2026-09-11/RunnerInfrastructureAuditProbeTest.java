package com.erp.runner.service;

import com.erp.runner.config.RunnerProperties;
import com.erp.runner.model.RunRecord;
import com.erp.runner.model.RunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InfrastructureAuditProbeTest {
    @TempDir Path dir;
    RunnerProperties props;
    PreflightService preflight;
    MavenExecutor executor;
    RunRecord run;

    void setup(boolean imported) throws Exception {
        props = new RunnerProperties();
        props.setLogsDir(dir.toString());
        props.setErpRepoPath(dir.toString());
        // A local no-op process; does not run Maven, ERP, TCM or Docker.
        Path stub = dir.resolve("audit-noop.cmd");
        Files.writeString(stub, "@exit /b 0\r\n");
        props.setMavenExecutable(stub.toString());
        preflight = mock(PreflightService.class);
        ProgressWatcher progress = mock(ProgressWatcher.class);
        TcmFallbackImportService shipper = mock(TcmFallbackImportService.class);
        when(shipper.resultsFileFor(anyString())).thenReturn(dir.resolve("results.jsonl"));
        when(shipper.ensureImported(any())).thenReturn(imported);
        executor = new MavenExecutor(props, preflight, progress, shipper);
        run = new RunRecord("audit", "dev", "smoke", null, null, 1L, 1L, "audit");
        run.setStatus(RunStatus.RUNNING);
    }

    @Test void successRequiresConfirmedImport() throws Exception {
        setup(false);
        executor.execute(run);
        assertNotEquals(RunStatus.SUCCESS, run.getStatus(), "Unconfirmed TCM delivery must not report SUCCESS");
    }

    @Test void cancellationDuringPreflightMustRemainCancelled() throws Exception {
        setup(true);
        doAnswer(invocation -> { executor.cancel(run); return null; }).when(preflight).check(run);
        executor.execute(run);
        assertEquals(RunStatus.CANCELLED, run.getStatus(), "Cancel during preflight was overwritten");
    }

    @Test void databaseOptOutMustReachTestJvm() throws Exception {
        setup(true);
        props.setUseDatabase(false);
        Method method = MavenExecutor.class.getDeclaredMethod("buildCommand", RunRecord.class);
        method.setAccessible(true);
        List<?> command = (List<?>) method.invoke(executor, run);
        assertTrue(command.contains("-Duse.database=false"), "Configured database opt-out was ignored");
    }
}
