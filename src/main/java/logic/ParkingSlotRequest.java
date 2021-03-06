package logic;

import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.parse4j.ParseException;
import org.parse4j.ParseGeoPoint;
import org.parse4j.ParseObject;


import data.management.DatabaseManager;
import data.members.StickersColor;
import util.Distance;


/**
 * 
 * @author assaflu
 * @since 11.5.2017
 * 
 * The purpose of this class is to implement the logic behind the parking slot request screen
 */

public class ParkingSlotRequest {

	private ParseGeoPoint destenation;
	private Date dateToPark;
	private int hoursAmunt;
	private DatabaseManager manager;
	
	public void setDestenation(ParseGeoPoint dest){
		this.destenation = dest;
	}
	
	public void setDate (Date date){
		this.dateToPark = date;
	}
	public Date getDate(){
		return this.dateToPark;
	}
	public int getHoursAmount(){
		return this.hoursAmunt;
	}
	
	public ParkingSlotRequest (ParseGeoPoint destenation,Date date,int hoursAmunt, DatabaseManager manager){
		this.manager = manager;
		this.destenation = destenation;
		this.dateToPark = date;
		this.hoursAmunt = hoursAmunt;
	}
	
	private Boolean orderInTime(ParseObject order){
		Calendar cal = Calendar.getInstance();
		cal.setTime(this.dateToPark);
		int wantedStartingHour = cal.get(Calendar.HOUR_OF_DAY);
		int wantedStartingQuarter = cal.get(Calendar.MINUTE);
		int wantedStartTime = wantedStartingHour*4+wantedStartingQuarter;
		SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd");
		
		int orderStartTime = order.getInt("hour");
		int orderTimeAmount = order.getInt("hoursAmount");
		int orderEndTime = orderStartTime+orderTimeAmount;
		String orderDate = order.getString("date");
		
		Boolean noValidParkingCondition = (orderStartTime == wantedStartTime);
		noValidParkingCondition = Boolean.logicalOr(noValidParkingCondition,(orderEndTime)%(24*4) == (wantedStartTime+this.hoursAmunt)%(24*4));
		noValidParkingCondition = Boolean.logicalOr(noValidParkingCondition,orderStartTime<wantedStartTime && (orderEndTime)%(24*4) > wantedStartTime);
		noValidParkingCondition = Boolean.logicalOr(noValidParkingCondition, wantedStartTime<orderStartTime && (wantedStartTime+this.hoursAmunt)%(24*4) > orderStartTime);
		noValidParkingCondition = Boolean.logicalAnd(noValidParkingCondition, formatDate.format(cal.getTime()).equals(orderDate));
		
		return (!noValidParkingCondition);
	}
	
	private List<String> noHourCollisionParking(List<ParseObject> tempListOrders, List<ParseObject> tempListParkingSlot){
		List<String> validParking = new ArrayList<String>();
		for(ParseObject p : tempListParkingSlot){
			validParking.add(p.getString("name"));
		}
		
		for(ParseObject p : tempListOrders){
			if(!orderInTime(p)){
				validParking.remove(p.getString("slotId"));
			}
		}
		
		return validParking;
	}
	
	/**
	 * Negative value represent no restriction
	 * @param maxDistance
	 * @param maxCost
	 * @return
	 */
	public List<PresentParkingSlot> getAllAvailableParkingSlot(Billing costCalculator){
		List<ParseObject> tempListParkingSlot = manager.getAllObjects("ParkingSlot", 600);
		List<ParseObject> tempListOrders = manager.getAllObjects("Order", 600);
		List<String> validParkings = this.noHourCollisionParking(tempListOrders, tempListParkingSlot);

		List<PresentParkingSlot> returnList = new ArrayList<PresentParkingSlot>();
		for(ParseObject p : tempListParkingSlot){
			String parkingName = p.getString("name");
			if(validParkings.contains(parkingName)){
				ParseGeoPoint location = p.getParseGeoPoint("location");
				StickersColor rank = StickersColor.values()[p.getInt("rank")];
				double ratting = p.getDouble("rating");
				int voters = p.getInt("numOfVoters");
				returnList.add(
						new PresentParkingSlot(parkingName, location.getLatitude(),location.getLongitude(),
								costCalculator.calculateCost(rank, Distance.AirDistance(location,this.destenation))*this.hoursAmunt/16,
								Distance.AirDistance(p.getParseGeoPoint("location"),this.destenation),ratting/voters)
						);
			}

			
		}
		return returnList;
	}

	public Boolean orderParkingSlot(String driverID, String slotID,double price) throws ParseException, InterruptedException{
		List<ParseObject> tempListOrders = manager.getAllObjects("Order", 600);
		if(!isParkingValid(slotID, tempListOrders)) return new Boolean(false);
		
		Calendar cal = Calendar.getInstance(); // creates calendar
	    cal.setTime(dateToPark); // sets calendar time/date
	    cal.add(Calendar.HOUR_OF_DAY, hoursAmunt/4);
	    cal.add(Calendar.MINUTE, hoursAmunt%4);
	    
		Date endTime =cal.getTime();
		new Order(driverID, slotID, dateToPark, endTime,price, manager);
		return new Boolean(true);
		
	}

	private boolean isParkingValid(String slotID, List<ParseObject> tempListOrders) {
		for(ParseObject p : tempListOrders){
			if(!orderInTime(p) && slotID.equals(p.getString("slotId"))){
				return false;
			}
				
		}
		return true;
	}

}
