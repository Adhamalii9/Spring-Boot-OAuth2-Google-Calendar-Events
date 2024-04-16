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

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import org.oauth.social.model.CalendarObj;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
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

import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.CLIENT_ID;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.CLIENT_SECRET;

@Controller
public class MainController {

	private static final String APPLICATION_NAME = "google-calender";
	private static HttpTransport httpTransport;
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static Calendar client;

	private static SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");

	GoogleClientSecrets clientSecrets;
	static GoogleAuthorizationCodeFlow flow;
	Credential credential;

	@Value("${google.client.client-id}")
	private String clientId;
	@Value("${google.client.client-secret}")
	private String clientSecret;
	@Value("${google.client.redirectUri}")
	private String redirectURI;


	private static final HttpTransport HTTP_TRANSPORT;

	// Initialize HTTP_TRANSPORT
	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		} catch (GeneralSecurityException | IOException e) {
			throw new RuntimeException("Failed to initialize Google HTTP Transport", e);
		}
	}

	private Set<Event> events = new HashSet<>();


	private static boolean isAuthorised = false;

	public void setEvents(Set<Event> events) {
		this.events = events;
	}


	private String authorize(String redirectURL, HttpServletRequest request) throws Exception {
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
	public RedirectView googleConnectionStatus(String redirectURL, HttpServletRequest request) throws Exception {
		return new RedirectView(authorize(redirectURI, request));
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
			DateTime now = new DateTime(System.currentTimeMillis());

			eventList = events.list("primary").setSingleEvents(true).setTimeMin(now).setOrderBy("startTime").execute();

			List<Event> items = eventList.getItems();

			CalendarObj calendarObj;
			List<CalendarObj> calendarObjs = new ArrayList<CalendarObj>();

			for (Event event : items) {

				DateTime startDateTime = event.getStart().getDateTime();
				DateTime endDateTime = event.getEnd().getDateTime();

				System.out.println(startDateTime + "  " + endDateTime);
				calendarObj = new CalendarObj();
				calendarObj.setTitle(event.getSummary());
				calendarObj.setStartDate(startDateTime);
				calendarObj.setEndDate(endDateTime);
				calendarObjs.add(calendarObj);


			}
			for (CalendarObj ca : calendarObjs) {
				System.out.println(ca.getTitle());
			}

			return calendarObjs;

		} catch (Exception e) {
			return new ArrayList<CalendarObj>();
		}
	}


	@PostMapping("/insertEvent")
	public String insertEvent() {
		try {
			// Check if the Google Authorization Flow has been initialized
			if (flow == null) {
				initializeGoogleAuthorizationFlow();
;
			}
			// Perform the OAuth2 authorization (if not already authorized)
			if (!isAuthorised) {
				throw new RuntimeException("Google Calendar API not authorized.");
			}

			// Obtain a valid Credential (using a fixed user ID)
			Credential credential = flow.loadCredential("userID");

			// Build the Google Calendar client
			Calendar calendar = buildCalendarClient(credential);

			// Create a new event
			Event event = buildEvent();

			// Insert the event into the primary calendar
			calendar.events().insert("primary", event).execute();

			System.out.println("Event inserted successfully!");
			return "redirect:/calendar"; // Redirect to calendar page after successful insertion
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to insert event: " + e.getMessage());
			return "error"; // Redirect to error page if insertion fails
		} catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

	private void initializeGoogleAuthorizationFlow() throws GeneralSecurityException, IOException {
		httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		Details web = new Details();
		web.setClientId(clientId);
		web.setClientSecret(clientSecret);
		clientSecrets = new GoogleClientSecrets().setWeb(web);
		flow = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, JSON_FACTORY, clientSecrets,
				Collections.singleton(CalendarScopes.CALENDAR))
				.build();
	}
	private Calendar buildCalendarClient(Credential credential) {
		return new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
				.setApplicationName(APPLICATION_NAME)
				.build();
	}
	private Event buildEvent() {
		LocalDateTime startDateTime = LocalDateTime.now();
		LocalDateTime endDateTime = startDateTime.plusHours(1);

		EventDateTime start = new EventDateTime()
				.setDateTime(new DateTime(Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant())))
				.setTimeZone("Your Time Zone");

		EventDateTime end = new EventDateTime()
				.setDateTime(new DateTime(Date.from(endDateTime.atZone(ZoneId.systemDefault()).toInstant())))
				.setTimeZone("Your Time Zone");

		return new Event()
				.setSummary("Event Summary")
				.setDescription("Event Description")
				.setStart(start)
				.setEnd(end);
	}




}