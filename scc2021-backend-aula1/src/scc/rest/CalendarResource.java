package scc.rest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.azure.cosmos.CosmosException;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cosmos.CosmosDBLayer;
import data.Calendar;
import data.Entity;
import data.Period;
import data.Reservation;
import scc.redis.CacheKeyNames;
import scc.redis.RedisCache;
import scc.utils.AdvanceFeatures;
import scc.utils.TableName;

@Path("/calendar")
public class CalendarResource {

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	public void createCalendar(Calendar calendar) {
		if (!ownerExists(calendar.getOwnerId())) {
			CosmosDBLayer<?> dbLayerCalendar = CosmosDBLayer.getInstance(Calendar.class);
			try {
				calendar.setId(UUID.randomUUID().toString());
				dbLayerCalendar.createItem(calendar, TableName.CALENDAR.getName());
			} catch (CosmosException e) {
				throw new WebApplicationException(Status.CONFLICT);
			}
		} else {
			throw new WebApplicationException(Response.Status.CONFLICT);
		}
	}

	@PUT
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public void updateCalendar(@PathParam("id") String id, Calendar calendar) {
		CosmosDBLayer<?> dbLayer = CosmosDBLayer.getInstance(Calendar.class);
		try {
			dbLayer.putItem(id, calendar, TableName.CALENDAR.getName());
		} catch (CosmosException e) {
			throw new WebApplicationException(Status.NOT_FOUND);
		}
	}

	@GET
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Calendar getCalendar(@PathParam("id") String id) {
		Calendar calendar = null;
		String key = id;
		boolean hasCache = Boolean.parseBoolean(AdvanceFeatures.getProperty(AdvanceFeatures.REDIS));
		String cacheItem = new String();
		if (hasCache)
			cacheItem = RedisCache.getCache().getItemFromCache(key);
		if(cacheItem.isEmpty() || !hasCache) { //calls the service
			CosmosDBLayer<?> dbLayer = CosmosDBLayer.getInstance(Calendar.class);
			CosmosPagedIterable<?> items = dbLayer.getItemById(id, TableName.CALENDAR.getName());
			for (Object item : items) {
				calendar = (Calendar) item;
			}
			if (calendar == null) {
				throw new WebApplicationException(Status.NOT_FOUND);
			} else {
				RedisCache.getCache().addItemToCache(calendar.getId(), calendar, 120);
			}
		} else { //retrieves from cache
			ObjectMapper mapper = new ObjectMapper();
			try {
				Calendar cal = mapper.readValue(cacheItem, Calendar.class);
				calendar = cal;
			} catch (JsonProcessingException e1) {
				e1.printStackTrace();
			}
		}
		return calendar;
	}

	// -----------------------------------AVAILABLE_PERIOD-----------------------------//

	@POST
	@Path("/period/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void setAvailablePeriod(@PathParam("id") String id, Period period) {
		CosmosDBLayer<?> dbLayerCalendar = CosmosDBLayer.getInstance(Calendar.class);
		CosmosPagedIterable<?> items = dbLayerCalendar.getItemById(id, TableName.CALENDAR.getName());
		Calendar calendar = null;
		for (Object item : items) {
			calendar = (Calendar) item;
		}
		if (calendar == null) {
			throw new WebApplicationException(Status.NOT_FOUND);
		} else {
			String periodId = UUID.randomUUID().toString();
			period.setId(periodId);
			// Define TTL
			LocalDateTime currDate = LocalDateTime.now();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
			LocalDateTime endDate = LocalDateTime.parse(period.getEndDate(), formatter);
			
			int ttl = (int) ChronoUnit.DAYS.between(currDate, endDate) * 60 * 24 * 30;
			period.setTtl(ttl);

			// add new available period
			CosmosDBLayer<?> dbLayerPeriod = CosmosDBLayer.getInstance(Period.class);
			dbLayerPeriod.putItem(periodId, period, TableName.PERIOD.getName());
		}
	}

	@GET
	@Path("/period/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Period getAvailablePeriod(@PathParam("id") String id) {
		Period period = null;
		String key = id;
		boolean hasCache = Boolean.parseBoolean(AdvanceFeatures.getProperty(AdvanceFeatures.REDIS));
		String cacheItem = new String();
		if (hasCache)
			cacheItem = RedisCache.getCache().getItemFromCache(key);
		if(cacheItem.isEmpty() || !hasCache) { //calls the service
			CosmosDBLayer<?> dbLayerPeriod = CosmosDBLayer.getInstance(Period.class);
			CosmosPagedIterable<?> items = dbLayerPeriod.getItemById(id, TableName.PERIOD.getName());
			for (Object item : items) {
				period = (Period) item;
			}
			if (period == null) {
				throw new WebApplicationException(Status.NOT_FOUND);
			} else {
				RedisCache.getCache().addItemToCache(period.getId(), period, 120);
			}
		} else { //retrieves from cache
			ObjectMapper mapper = new ObjectMapper();
			try {
				Period per = mapper.readValue(cacheItem, Period.class);
				period = per;
			} catch (JsonProcessingException e1) {
				e1.printStackTrace();
			}
		}
		return period;
	}

	// ---------------------------------RESERVATION----------------------------------//

	@POST
	@Path("/reservation/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void addReservation(@PathParam("id") String id, Reservation reservation) {
		CosmosDBLayer<?> dbLayer = CosmosDBLayer.getInstance(Calendar.class);
		CosmosPagedIterable<?> items = dbLayer.getItemById(id, TableName.CALENDAR.getName());
		Calendar calendar = null;
		for (Object item : items) {
			calendar = (Calendar) item;
		}
		if (calendar == null) {
			throw new WebApplicationException(Status.NOT_FOUND);
		} else {
			Period availablePeriod = getAvailablePeriodQuery(reservation);
			if (availablePeriod == null) {
				throw new WebApplicationException(Status.CONFLICT);
			} else {
				String reservationId = UUID.randomUUID().toString();
				reservation.setId(reservationId);
				reservation.setPeriodId(availablePeriod.getId());
				// Define TTL
				LocalDateTime currDate = LocalDateTime.now();
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
				LocalDateTime endDate = LocalDateTime.parse(reservation.getEndDate(), formatter);
				
				int ttl = (int) ChronoUnit.DAYS.between(currDate, endDate) * 60 * 24 * 30;
				reservation.setTtl(ttl);

				// LocalDateTime.now(ZoneOffset.UTC);

				// add new reservation
				CosmosDBLayer<?> dbLayerReservation = CosmosDBLayer.getInstance(Reservation.class);
				dbLayerReservation.putItem(reservationId, reservation, TableName.RESERVATION.getName());
			}
		}
	}

	@DELETE
	@Path("/reservation/cancel/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void cancelReservation(@PathParam("id") String id, Reservation reservation) {
		CosmosDBLayer<?> dbLayer = CosmosDBLayer.getInstance(Calendar.class);
		CosmosPagedIterable<?> items = dbLayer.getItemById(id, TableName.CALENDAR.getName());
		Calendar calendar = null;
		for (Object item : items) {
			calendar = (Calendar) item;
		}
		if (calendar == null) {
			throw new WebApplicationException(Status.NOT_FOUND);
		} else {
			if (getReservationPeriod(reservation) == null) {
				throw new WebApplicationException(Status.NOT_FOUND);
			} else {
				// delete reservation
				CosmosDBLayer<?> dbLayerReservation = CosmosDBLayer.getInstance(Reservation.class);
				dbLayerReservation.delItem(reservation.getId(), TableName.RESERVATION.getName());
			}
		}
	}

	@GET
	@Path("/reservation/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Reservation getReservation(@PathParam("id") String id) {
		Reservation reservation = null;
		String key = id;
		boolean hasCache = Boolean.parseBoolean(AdvanceFeatures.getProperty(AdvanceFeatures.REDIS));
		String cacheItem = new String();
		if (hasCache)
			cacheItem = RedisCache.getCache().getItemFromCache(key);
		if(cacheItem.isEmpty() || !hasCache) { //calls the service
			CosmosDBLayer<?> dbLayerReservation = CosmosDBLayer.getInstance(Reservation.class);
			CosmosPagedIterable<?> items = dbLayerReservation.getItemById(id, TableName.RESERVATION.getName());
			for (Object item : items) {
				reservation = (Reservation) item;
			}
			if (reservation == null) {
				throw new WebApplicationException(Status.NOT_FOUND);
			} else {
				RedisCache.getCache().addItemToCache(reservation.getId(), reservation, 120);
			}
		} else { //retrieves from cache
			ObjectMapper mapper = new ObjectMapper();
			try {
				Reservation res = mapper.readValue(cacheItem, Reservation.class);
				reservation = res;
			} catch (JsonProcessingException e1) {
				e1.printStackTrace();
			}
		}
		return reservation;
	}

	// ---------------------------------AUX
	// FUNCTIONS----------------------------------//

	private Period getAvailablePeriodQuery(Reservation reservation) {
		CosmosDBLayer<?> dbLayerPeriod = CosmosDBLayer.getInstance(Period.class);
		CosmosPagedIterable<?> items = dbLayerPeriod.getItemsBySpecialQuery( // Query to find available periods for the
																				// reservation
				"SELECT * FROM " + TableName.PERIOD.getName() + " WHERE " + TableName.PERIOD.getName() + ".id=\""
						+ reservation.getPeriodId() + "\"",
				TableName.PERIOD.getName());
		CosmosDBLayer<?> dbLayerReservation = CosmosDBLayer.getInstance(Reservation.class);
		String query = // Query to find overlapping reservations for that period
				"SELECT * FROM %1$s WHERE %1$s.periodId=\"" + "%2$s" + "\"" + " AND ((" + // 1st case overlap: exactly
																							// equal or totally inside
																							// an existent res
						"%1$s.startDate<=\"" + reservation.getStartDate() + "\"" + " AND %1$s.endDate>=\""
						+ reservation.getEndDate() + "\") OR (" // 2nd case: right overlap
						+ "%1$s.startDate>=\"" + reservation.getStartDate() + "\"" + " AND %1$s.startDate<=\""
						+ reservation.getEndDate() + "\") OR (" // 3rd case: left overlap
						+ "%1$s.startDate<=\"" + reservation.getStartDate() + "\"" + " AND %1$s.endDate>=\""
						+ reservation.getStartDate() + "\"))";
		Period period = null;
		for (Object item : items) {
			period = (Period) item;
			CosmosPagedIterable<?> itemsRes = dbLayerReservation.getItemsBySpecialQuery(
					String.format(query, TableName.RESERVATION.getName(), period.getId()),
					TableName.RESERVATION.getName());
			Reservation res = null;
			for (Object itemRes : itemsRes) {
				res = (Reservation) itemRes;
			}
			if (res == null) { // no overlapping reservations were found, this period is free for the
								// reservation
				break;
			}
		}
		return period;
	}

	private Period getReservationPeriod(Reservation reservation) {
		CosmosDBLayer<?> dbLayerPeriod = CosmosDBLayer.getInstance(Period.class);
		String query = "SELECT * FROM " + TableName.PERIOD.getName() + " WHERE " + TableName.PERIOD.getName() + ".id=\""
				+ reservation.getPeriodId() + "\"";
		CosmosPagedIterable<?> items = dbLayerPeriod.getItemsBySpecialQuery(query, TableName.PERIOD.getName());
		Period period = null;
		for (Object item : items) {
			period = (Period) item;
		}
		return period;
	}

	private boolean ownerExists(String id) {
		CosmosDBLayer<?> dbLayerEntity = CosmosDBLayer.getInstance(Entity.class);
		CosmosPagedIterable<?> items = dbLayerEntity.getItemById(id, TableName.ENTITY.getName());
		Entity entity = null;
		for (Object item : items) {
			entity = (Entity) item;
		}
		if (entity == null)
			return false;
		else {
			return true;
		}
	}
}
