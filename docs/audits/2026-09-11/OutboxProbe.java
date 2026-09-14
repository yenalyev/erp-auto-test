import java.nio.file.*;
import java.time.LocalDateTime;
import com.erp.utils.helpers.TcmResultOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;

class OutboxProbe {
  public static void main(String[] args) throws Exception {
    Path directory = Files.createTempDirectory(Path.of("target/infrastructure-audit"), "outbox-probe-");
    Path file = directory.resolve("results.jsonl");
    System.setProperty("tcm.results.file", file.toAbsolutePath().toString());
    TcmResultOutbox.append("TC-AUDIT-001", "FAIL", 1L, "expected\tactual", LocalDateTime.now());
    try {
      new ObjectMapper().readTree(Files.readString(file));
      System.out.println("TAB_OUTBOX_VALID_JSON=true");
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      System.out.println("TAB_OUTBOX_VALID_JSON=false; failure=" + ex.getClass().getSimpleName());
    }
  }
}
