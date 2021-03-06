package pl.edu.agh.io.pdptw.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.io.pdptw.algorithm.scheduling.DriveFirstScheduler;
import pl.edu.agh.io.pdptw.algorithm.scheduling.Scheduler;
import pl.edu.agh.io.pdptw.logging.LoggingUtils;

@Data
@EqualsAndHashCode
@AllArgsConstructor

public class Vehicle {
    private final String id;
    private final Integer maxCapacity;
    private Location location;
    private final Location startLocation;
    private Route route;
    private Set<Integer> servedRequestsIds;
    
    /* note the static keyword */
    @Setter @Getter private static Scheduler scheduler = new DriveFirstScheduler();
    
	public Vehicle(String id, Integer maxCapacity,
			Location startLocation) {
		
		this(id, maxCapacity, startLocation, startLocation, 
				new Route(new ArrayList<>()), new HashSet<>());
	}
	
	public List<Request> removeFinishedRequests(int time, boolean shouldLog) {
		
		/* note that we leave the pickup requests
		 * that has already been served but have corresponding
		 * delivery requests which are yet to be served! */
		
		List<Request> removedRequests = route.getRequests()
				.stream()
				.filter(r -> {
					DeliveryRequest delivery = (DeliveryRequest) 
							((r.getType() == RequestType.DELIVERY)
									? r : r.getSibling());
					return delivery.getRealizationTime() + delivery.getServiceTime() <= time;
				})
				.collect(Collectors.toList());
		
		if (shouldLog) {
			StringBuilder builder = new StringBuilder();
			removedRequests.forEach(r -> builder.append(r.getId() + ", "));
			
			if (builder.length() > 0) {
				LoggingUtils.info("Vehicle [" + id 
						+ "] has finished realization of the following requests: \n" 
						+ "[" + builder.toString() + "]");
			}
		}
		
		servedRequestsIds.addAll(route.getRequests()
				.stream()
				.filter(r -> r.getRealizationTime() + r.getServiceTime() <= time)
				.map(r -> r.getId())
				.collect(Collectors.toSet()));
		route = new Route(route.getRequests()
				.stream()
				.filter(r -> {
					DeliveryRequest delivery = (DeliveryRequest) 
							((r.getType() == RequestType.DELIVERY)
									? r : r.getSibling());
					return delivery.getRealizationTime() + delivery.getServiceTime() > time;
				})
				.collect(Collectors.toList()));
		scheduler.scheduleRequests(this, time);
		return removedRequests;
	}
	
	public void removeRequestsByIds(List<Integer> requestsIds, int time) {
		
		/* save only those requests whose
		 * ids are not stored in the requestsIds list */
		
		route = new Route(route.getRequests()
				.stream()
				.filter(r -> !requestsIds.contains(r.getId()))
				.collect(Collectors.toList()));
		scheduler.scheduleRequests(this, time);
	}
	
	/* the feasibility of the insertion of the new request pair
	 * is checked as follows: 
	 * 1. look for the insertion position of the pickup request
	 * and calculate the total volume of the products being transported
	 * 
	 * 2. add the volume of the products delivered while realizing 
	 * the new pickup - delivery requests to the total volume
	 *  
	 * 3. propagate the change of the realization times after 
	 * inserting the pickup requests (it's not actually inserted, 
	 * we only calculate the change of the time parameters)
	 * 
	 * 4. find the insertion position of the new delivery request
	 * 
	 * 5. continue propagating the changes */
	
	public boolean isInsertionPossible(PickupRequest pickupRequest, int pickupPosition, int deliveryPosition) {
		List<Request> requests = route.getRequests();
		
		assert pickupPosition <= route.getRequests().size();
		assert deliveryPosition <= route.getRequests().size();
		assert pickupPosition < deliveryPosition;
		assert pickupPosition <= requests.size();
		assert deliveryPosition <= requests.size() + 1;
		
		/* Check whether:
		 * - the total volume of packages
		 * is less or equal to the maximum capacity of
		 * the vehicle. 
		 * - updated realization times satisfy the 
		 * time window constraints (realizationTime <= timeWindowEnd)
		 */


		
		boolean insertionPossible = true;
		Request deliveryRequest = pickupRequest.getSibling();
		Request prev = null;
		Request cur = null;
		Request pickupCopy = pickupRequest.createShallowCopy();
		double totalVolume = 0;
		Iterator<Request> it = requests.iterator();
		int prevOriginalRealizationTime = 0;
		int curOriginalRealizationTime = 0;
		int counter = 0;
		
		/* we must not allow to insert a new request
		 * befor an already served one */
		
		if (pickupPosition < requests.size()) {
			Request nextRequest = requests.get(pickupPosition);
			insertionPossible = insertionPossible 
					&& !servedRequestsIds.contains(nextRequest.getId());
		}
		
		while (insertionPossible
				&& it.hasNext() 
				&& counter < pickupPosition) {
			
			prev = it.next();
			totalVolume += prev.getVolume();
			insertionPossible = (totalVolume <= maxCapacity);
			counter++;
		}
		
		if (prev != null) {
			scheduler.updateSuccessor(prev, pickupCopy);
			
		} else {
			/* if the pickup request will potentially be
			 * the first one on the route we should
			 * update it's realization time based
			 * on the distance between the initial vehicle's
			 * position and the request's location */
			
			scheduler.updateRequestRealizationTime(pickupCopy, 
					(int) Location.calculateDistance(
							this.startLocation, pickupCopy.getLocation()));
		}
		
		totalVolume += pickupCopy.getVolume();
		insertionPossible = insertionPossible
				&& (pickupCopy.getRealizationTime() <= pickupCopy.getTimeWindowEnd())
				&& (totalVolume <= maxCapacity);
		
		prev = pickupCopy;
		prevOriginalRealizationTime = pickupRequest.getRealizationTime();
		counter++;
		
		/* propagate the potential change of the 
		 * realization times on the whole route
		 * 
		 * note the need for restoring the original 
		 * realization times after finishing that! */
		
		while (insertionPossible 
				&& it.hasNext()
				&& counter < deliveryPosition) {
			
			cur = it.next();
			totalVolume += cur.getVolume();
			curOriginalRealizationTime = cur.getRealizationTime();
			scheduler.updateSuccessor(prev, cur);
			insertionPossible = (cur.getRealizationTime() <= cur.getTimeWindowEnd())
					&& (totalVolume <= maxCapacity);
			prev.setRealizationTime(prevOriginalRealizationTime);
			prev = cur;
			prevOriginalRealizationTime = curOriginalRealizationTime;
			counter++;
		}
		
		curOriginalRealizationTime = deliveryRequest.getRealizationTime();
		scheduler.updateSuccessor(prev, deliveryRequest);
		prev.setRealizationTime(prevOriginalRealizationTime);
		prevOriginalRealizationTime = curOriginalRealizationTime;
		totalVolume += deliveryRequest.getVolume();
		prev = deliveryRequest;
		
		insertionPossible = insertionPossible 
				&& (deliveryRequest.getRealizationTime() <= deliveryRequest.getTimeWindowEnd())
				&& (totalVolume <= maxCapacity);
		
		while (insertionPossible 
				&& it.hasNext()) {
			
			cur = it.next();
			totalVolume += cur.getVolume();
			curOriginalRealizationTime = cur.getRealizationTime();
			scheduler.updateSuccessor(prev, cur);
			insertionPossible = (cur.getRealizationTime() <= cur.getTimeWindowEnd())
					&& (totalVolume <= maxCapacity);
			prev.setRealizationTime(prevOriginalRealizationTime);
			prev = cur;
			prevOriginalRealizationTime = curOriginalRealizationTime;
			counter++;
		}
		
		if (prev != null) {
			prev.setRealizationTime(prevOriginalRealizationTime);
		}
		
		return insertionPossible;
	}
	
	public void updateRealizationTimes() {
		scheduler.scheduleRequests(this, 0);
	}
	
	public void insertRequest(PickupRequest pickupRequest, int pickupPosition, int deliveryPosition) {
		List<Request> requests = route.getRequests();
		requests.add(pickupPosition, pickupRequest);
		requests.add(deliveryPosition, pickupRequest.getSibling());
		updateRealizationTimes();
	}
	
	public Request removeRequest(int pickupPosition, int deliveryPosition) {
		List<Request> requests = route.getRequests();
		
		assert pickupPosition >= 0;
		assert deliveryPosition >= 0;
		assert pickupPosition < requests.size();
		assert deliveryPosition < requests.size();
		
		Request pickup = requests.remove(pickupPosition);
		Request delivery = pickup.getSibling();
		
		pickup.setRealizationTime(pickup.getTimeWindowStart());
		delivery.setRealizationTime(delivery.getTimeWindowStart());
		
		/* [deliveryPosition -1] because after removing the pickup request
		 * the position of the delivery request is decremented
		 * by 1. Note that pickup request is always added
		 * to the route before the corresponding delivery request. */
		
		requests.remove(deliveryPosition - 1);
		updateRealizationTimes();
		
		return pickup;
	}
	
	public Request removeRequest(int pickupPosition) {
		List<Request> requests = route.getRequests();
		
		assert pickupPosition >= 0;
		assert pickupPosition < requests.size();
		
		Request pickup = requests.get(pickupPosition);
		Request delivery = pickup.getSibling();
		
		pickup.setRealizationTime(pickup.getTimeWindowStart());
		delivery.setRealizationTime(delivery.getTimeWindowStart());

		requests.remove(pickupPosition);
		
		/* [deliveryPosition -1] because after removing the pickup request
		 * the position of the delivery request is decremented
		 * by 1. Note that pickup request is always added
		 * to the route before the corresponding delivery request. */
		
		requests.remove(delivery);
		updateRealizationTimes();
		
		return pickup;
	}
	
	public RequestPositions removeRequest(PickupRequest pickupRequest) {
		List<Request> requests = route.getRequests();
		
		assert requests.contains(pickupRequest);
		assert requests.contains(pickupRequest.getSibling());
		
		int pickupPosition = requests.indexOf(pickupRequest);
		int deliveryPosition = requests.indexOf(pickupRequest.getSibling());
		requests.remove(pickupRequest);
		requests.remove(pickupRequest.getSibling());
		updateRealizationTimes();
		
		pickupRequest.setRealizationTime(pickupRequest.getTimeWindowStart());
		pickupRequest.getSibling().setRealizationTime(
				pickupRequest.getSibling().getTimeWindowStart());
		
		return new RequestPositions(pickupPosition, deliveryPosition);
	}
    
	@Override
	public String toString() {
		String representation = String.format(
				"id: %s, max capacity: %d, location: (%d, %d), route: \n[\n",
				id, maxCapacity, location.getX(), location.getY());
		List<String> requestsStrings = route.getRequests().stream()
				.map(Request::toString)
				.collect(Collectors.toList());
		
		for (String req : requestsStrings) {
			representation += "\t" + req + "\n";
		}
		
		representation += "]";
		return representation;
	}
	
	public Vehicle createShallowCopy() {
		Vehicle copy = new Vehicle(id, maxCapacity, startLocation);
		copy.setLocation(location);
		copy.setRoute(route);
		
		return copy;
	}
	
	public Vehicle copy() {
		Vehicle copy = new Vehicle(id, maxCapacity, startLocation);
		copy.setLocation(location);
		copy.setRoute(route.copy());
		
		return copy;
	}
	
	public Request getCurrentRequest(int time) {
		if (route.getRequests().size() > 0) {
			Iterator<Request> it = route.getRequests().iterator();
			Request current = it.next();
			Request prev = current;
			
			while (it.hasNext() && current.getRealizationTime() < time) {
				current = it.next();
				prev = current;
			}
			
			return prev;
		}
		
		return null;
	}
}
