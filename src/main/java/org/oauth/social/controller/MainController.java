package org.oauth.social.controller;

import java.io.IOException;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import org.oauth.social.model.CalendarObj;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.Calendar.Events;
import com.google.api.services.calendar.model.Event;

@Controller
public class MainController {

	private static final String APPLICATION_NAME = "";
	private static HttpTransport httpTransport;
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static Calendar client;

	private static SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");

	GoogleClientSecrets clientSecrets;
	GoogleAuthorizationCodeFlow flow;
	Credential credential;

	@Value("${google.client.client-id}")
	private String clientId;
	@Value("${google.client.client-secret}")
	private String clientSecret;
	@Value("${google.client.redirectUri}")
	private String redirectURI;
	@Value("${google.client.redirectUri.available.slot}")
	private String redirectURIAvailableSlot;

	private Set<Event> events = new HashSet<>();

	private final int START_HOUR = 8;
	private final int START_MIN = 00;
	private final int END_HOUR = 20;
	private final int END_MIN = 00;

	private static boolean isAuthorised = false;

	public void setEvents(Set<Event> events) {
		this.events = events;
	}

	private String authorize(String redirectURL) throws Exception {
		AuthorizationCodeRequestUrl authorizationUrl;
		if (flow == null) {
			Details web = new Details();
			web.setClientId(clientId);
			web.setClientSecret(clientSecret);
			clientSecrets = new GoogleClientSecrets().setWeb(web);
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets,
					Collections.singleton(CalendarScopes.CALENDAR)).build();
		}
		authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectURL);

		isAuthorised = true;

		return authorizationUrl.build();
	}

	@RequestMapping(value = "/calendar", method = RequestMethod.GET)
	public RedirectView googleConnectionStatus(HttpServletRequest request) throws Exception {
		return new RedirectView(authorize(redirectURI));
	}

	@RequestMapping(value = "/calendar", method = RequestMethod.GET, params = "code")
	public String oauth2Callback(@RequestParam(value = "code") String code, Model model) {
		if (isAuthorised) {
			try {

				model.addAttribute("title", "Today's Calendar Events");
				model.addAttribute("calendarObjs", getTodaysCalendarEventList(code, redirectURI));

			} catch (Exception e) {
				model.addAttribute("calendarObjs", new ArrayList<CalendarObj>());
			}

			return "agenda";
		} else {
			return "/";
		}
	}


	public Set<Event> getEvents() throws IOException {
		return this.events;
	}

	@RequestMapping(value = "/error", method = RequestMethod.GET)
	public String accessDenied(Model model) {

		model.addAttribute("message", "Not authorised.");
		return "login";

	}

	@RequestMapping(value = {"/", "/login", "/logout"}, method = RequestMethod.GET)
	public String login(Model model) {
		isAuthorised = false;

		return "login";
	}


	private List<CalendarObj> getTodaysCalendarEventList(String calenderApiCode, String redirectURL) {
		try {
			com.google.api.services.calendar.model.Events eventList;

			LocalDateTime localDateTime = LocalDateTime.ofInstant(new Date().toInstant(), ZoneId.systemDefault());
			LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
			LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);

			DateTime date1 = new DateTime(Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant()));
			DateTime date2 = new DateTime(Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant()));

			TokenResponse response = flow.newTokenRequest(calenderApiCode).setRedirectUri(redirectURL).execute();
			credential = flow.createAndStoreCredential(response, "userID");
			client = new Calendar.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
			Events events = client.events();
			eventList = events.list("primary").setSingleEvents(true).setTimeMin(date1).setTimeMax(date2).setOrderBy("startTime").execute();

			List<Event> items = eventList.getItems();

			CalendarObj calendarObj ;
			List<CalendarObj> calendarObjs = new ArrayList<CalendarObj>();

			for (Event event : items) {

				DateTime startDateTime =event.getStart().getDateTime();
				DateTime endDateTime = event.getEnd().getDateTime();

				System.out.println(startDateTime + "  " + endDateTime);
				calendarObj = new CalendarObj();
				calendarObj.setTitle(event.getSummary());
				calendarObj.setStartDate(startDateTime);
				calendarObj.setEndDate(endDateTime);
				calendarObjs.add(calendarObj);


			}
			for (CalendarObj ca : calendarObjs){
				System.out.println(ca.getTitle());
			}

			return calendarObjs;

		} catch (Exception e) {
			return new ArrayList<CalendarObj>();
		}
	}

	@RequestMapping(value = "/insert" , method = RequestMethod.POST)
	public void insertEvent() throws GeneralSecurityException, IOException {
		Event event = new Event()
				.setSummary("Google I/O 2015")
				.setLocation("800 Howard St., San Francisco, CA 94103")
				.setDescription("A chance to hear more about Google's developer products.");

		DateTime startDateTime = new DateTime("2015-05-28T09:00:00-07:00");
		EventDateTime start = new EventDateTime()
				.setDateTime(startDateTime)
				.setTimeZone("America/Los_Angeles");
		event.setStart(start);

		DateTime endDateTime = new DateTime("2015-05-28T17:00:00-07:00");
		EventDateTime end = new EventDateTime()
				.setDateTime(endDateTime)
				.setTimeZone("America/Los_Angeles");
		event.setEnd(end);

		String[] recurrence = new String[] {"RRULE:FREQ=DAILY;COUNT=2"};
		event.setRecurrence(Arrays.asList(recurrence));

		EventAttendee[] attendees = new EventAttendee[] {
				new EventAttendee().setEmail("lpage@example.com"),
				new EventAttendee().setEmail("sbrin@example.com"),
		};
		event.setAttendees(Arrays.asList(attendees));

		EventReminder[] reminderOverrides = new EventReminder[] {
				new EventReminder().setMethod("email").setMinutes(24 * 60),
				new EventReminder().setMethod("popup").setMinutes(10),
		};
		Event.Reminders reminders = new Event.Reminders()
				.setUseDefault(false)
				.setOverrides(Arrays.asList(reminderOverrides));
		event.setReminders(reminders);

		String calendarId = "primary";
		/*TokenResponse response = flow.newTokenRequest(calenderApiCode).setRedirectUri(redirectURL).execute();
		credential = flow.createAndStoreCredential(response, "userID");
		client = new Calendar.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
		Events events = client.events();
		event = client.events().insert(calendarId, event).execute();
		System.out.printf("Event created: %s\n", event.getHtmlLink());*/
	}
}
