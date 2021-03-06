package play.data.binding.types;

import org.junit.Test;
import play.mvc.Http.Request;
import play.mvc.Scope.Session;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static play.mvc.Http.Request.createRequest;

public class LocalDateTimeBinderTest {

  private LocalDateTimeBinder binder = new LocalDateTimeBinder();
  Request request = createRequest(null, "GET", "/", "", null, null, null, null, false, 80, "localhost", false, null, null);
  Session session = new Session();

  @Test
  public void nullLocalDateTime() {
    assertNull(binder.bind(request, session, "event.start", null, null, LocalDateTime.class, null));
  }

  @Test
  public void emptyLocalDateTime() {
    assertNull(binder.bind(request, session, "event.start", null, "", LocalDateTime.class, null));
  }

  @Test
  public void blankLocalDateTime() {
    assertNull(binder.bind(request, session, "event.start", null, " ", LocalDateTime.class, null));
  }

  @Test
  public void validLocalDateTime() {
    LocalDateTime expected = LocalDateTime.of(2014, 3, 8, 12, 49, 21);
    LocalDateTime actual = binder.bind(request, session, "event.start", null, "2014-03-08T12:49:21", LocalDateTime.class, null);
    assertEquals(expected, actual);
  }

  @Test
  public void validLocalDateTimeWithMilliseconds() {
    LocalDateTime expected = LocalDateTime.of(2014, 3, 8, 12, 49, 21, 130000000);
    LocalDateTime actual = binder.bind(request, session, "event.start", null, "2014-03-08T12:49:21.130", LocalDateTime.class, null);
    assertEquals(expected, actual);
  }

  @Test(expected = DateTimeParseException.class)
  public void invalidLocalDateTime() {
    binder.bind(request, session, "event.start", null, "2007-13-03T10:15:30", LocalDateTime.class, null);
  }
}