package org.pih.warehouse.shipping;

import grails.converters.JSON
import org.pih.warehouse.core.Location;

import org.pih.warehouse.inventory.Warehouse;
import org.pih.warehouse.product.Product;

class ShipmentController {
   
    def scaffold = Shipment
    def shipmentService

	
	def create = {
		def shipmentInstance = new Shipment()
		shipmentInstance.properties = params
		
		if (params.type == "incoming") { 
			shipmentInstance.destination = session.warehouse;			
		}
		else if (params.type == "outgoing") { 
			shipmentInstance.origin = session.warehouse;			
		}		
		//return [shipmentInstance: shipmentInstance]
		render(view: "create", model: [ shipmentInstance : shipmentInstance,
				warehouses : Warehouse.list(), eventTypes : EventType.list()]);
	}

	def save = {
		def shipmentInstance = new Shipment(params)
				
		if (shipmentInstance.save(flush: true)) {
		
			// Try to add the current event
			def eventType = EventType.get(params.eventType.id);
			if (eventType) {
				def shipmentEvent = new ShipmentEvent(eventType: eventType, eventLocation: session.warehouse, eventDate: new Date())
				shipmentInstance.addToEvents(shipmentEvent).save(flush:true);
			}
			flash.message = "${message(code: 'default.created.message', args: [message(code: 'shipment.label', default: 'Shipment'), shipmentInstance.id])}"
			redirect(action: "showDetails", id: shipmentInstance.id)
		}
		else {
			//redirect(action: "create", model: [shipmentInstance: shipmentInstance], params: [type:params.type])
			render(view: "create", model: [shipmentInstance : shipmentInstance,
				warehouses : Warehouse.list(), eventTypes : EventType.list()]);
		}
	}

	def update = {
		
		log.info params
		
		def shipmentInstance = Shipment.get(params.id)
		if (shipmentInstance) {
			if (params.version) {
				def version = params.version.toLong()
				if (shipmentInstance.version > version) {					
					shipmentInstance.errors.rejectValue("version", "default.optimistic.locking.failure", [message(code: 'shipment.label', default: 'Shipment')] as Object[], "Another user has updated this Shipment while you were editing")
					render(view: "editDetails", model: [shipmentInstance: shipmentInstance])
					return
				}
			}
			shipmentInstance.properties = params
			if (!shipmentInstance.hasErrors() && shipmentInstance.save(flush: true)) {
				flash.message = "${message(code: 'default.updated.message', args: [message(code: 'shipment.label', default: 'Shipment'), shipmentInstance.id])}"
				redirect(action: "showDetails", id: shipmentInstance.id)
			}
			else {
				render(view: "editDetails", model: [shipmentInstance: shipmentInstance])
			}
		}
		else {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'shipment.label', default: 'Shipment'), params.id])}"
			redirect(action: "list")
		}
	}

	
		
	def showDetails = {
		def shipmentInstance = Shipment.get(params.id)
		if (!shipmentInstance) {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'shipment.label', default: 'Shipment'), params.id])}"
			redirect(action: (params.type == "incoming") ? "listIncoming" : "listOutgoing")
		}
		else {
			[shipmentInstance: shipmentInstance]
		}
	}

	def editDetails = {
		def shipmentInstance = Shipment.get(params.id)
		if (!shipmentInstance) {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'shipment.label', default: 'Shipment'), params.id])}"
			redirect(action: (params.type == "incoming") ? "listIncoming" : "listOutgoing")
		}
		else {
			[shipmentInstance: shipmentInstance]
		}
	}

	
	def showPackingList = { 
		def shipmentInstance = Shipment.get(params.id)
		if (!shipmentInstance) {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'shipment.label', default: 'Shipment'), params.id])}"
			redirect(action: (params.type == "incoming") ? "listIncoming" : "listOutgoing")
		}
		else {
			[shipmentInstance: shipmentInstance]
		}
	}
	
	def editContents = {
		def shipmentInstance = Shipment.get(params.id)
		def containerInstance = Container.get(params?.container?.id);
		
		if (!shipmentInstance) {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'shipment.label', default: 'Shipment'), params.id])}"
			redirect(action: (params.type == "incoming") ? "listIncoming" : "listOutgoing")
		}
		else {
			[shipmentInstance: shipmentInstance, containerInstance: containerInstance]
		}
	}
	
	
	

	
	
	def listIncoming = { 
		def currentLocation = Location.get(session.warehouse.id);
		def incomingShipments = shipmentService.getShipmentsWithDestination(currentLocation);		
		[
			shipmentInstanceList : incomingShipments,
			shipmentInstanceTotal : incomingShipments.size(),
		];
	}
	
	
	def listOutgoing = { 
		def currentLocation = Location.get(session.warehouse.id);		
		def outgoingShipments = shipmentService.getShipmentsWithOrigin(currentLocation);		
		[
			shipmentInstanceList : outgoingShipments,
			shipmentInstanceTotal : outgoingShipments.size(),
		];
		
		
	}
    
    
    def list = { 
    	def browseBy = params.id;
    	def currentLocation = Location.get(session.warehouse.id);    	
    	log.debug ("current location" + currentLocation.name)    	
    	def allShipments = shipmentService.getShipmentsWithLocation(currentLocation);
		def incomingShipments = shipmentService.getShipmentsWithDestination(currentLocation);	
		def outgoingShipments = shipmentService.getShipmentsWithOrigin(currentLocation);			
		def shipmentInstanceList = ("incoming".equals(browseBy)) ? incomingShipments : 
			("outgoing".equals(browseBy)) ? outgoingShipments : allShipments;		
		// Arrange shipments by status 
		def shipmentListByStatus = new HashMap<String, ListCommand>();
		allShipments.each {
			def shipmentList = shipmentListByStatus[it.shipmentStatus];
			if (!shipmentList) {
				shipmentList = new ListCommand(category: it.shipmentStatus.name, color: it.shipmentStatus.color, 
					sortOrder: it.shipmentStatus.sortOrder, objectList: new ArrayList());		
			}
			shipmentList.objectList.add(it);	
			shipmentListByStatus.put(it.shipmentStatus, shipmentList)		
		}
		
		// Get a count of shipments by status		 
		// QUERY: select shipment_status.id, count(*) from shipment group by shipment_status.id 
			
		def criteria = Shipment.createCriteria()
		def results = criteria {			
			projections {
				groupProperty("shipmentStatus")
				count("shipmentStatus", "shipmentCount") //Implicit alias is created here !
			}
			//order 'myCount'
		}			
			
		[ 	results : results, shipmentInstanceList : shipmentInstanceList,
			shipmentInstanceTotal : allShipments.size(), shipmentListByStatus : shipmentListByStatus,
			incomingShipmentCount : incomingShipments.size(), outgoingShipmentCount : outgoingShipments.size()
		]
    }
	
	def sendShipment = { 
		redirect action: "showDetails", id: shipment.id;
	}

	def deliverShipment = {
		redirect action: "showDetails", id: shipment.id;
	}    
        
    
    def addShipmentAjax = {
		try {
		    //def newPost = postService.createPost(session.user.userId, params.content);
		    //def recentShipments = Shipment.findAllBy(session.user, [sort: 'id', order: 'desc', max: 20])
		    //render(template:"list", collection: recentShipments, var: 'shipment')
		    render { div(class:"errors", "success message") }
		} catch (Exception e) {
		    render { div(class:"errors", e.message) }
		}
    }
    
    def availableItems = {     		
    	log.debug params;
    	def items = null;
    	if (params.query) { 
	    	items = Product.findAllByNameLike("%${params.query}%");
	    	items = items.collect() {
	    		[id:it.id, name:it.name]
	    	}
    	}
    	def jsonItems = [result: items]    	
    	render jsonItems as JSON;    		
    }
    
    def addItemAutoComplete = {     		
    	log.info params;    	
		def container = Container.get(params.container.id);
    	def product = Product.get(params.selectedItem_id)
    	def shipment = Shipment.get(params.id);
    	log.debug "containers: " + shipment.getContainers()
    	//def container = shipment.containers[0];
    	if (container) { 
 	    	def shipmentItem = new ShipmentItem(product: product, quantity: 1);
	    	container.addToShipmentItems(shipmentItem).save(flush:true);
    	}
		else { 
			flash.message = "could not add item to container";
		}
		
    	redirect action: "editContents", id: shipment.id, params: ["container.id": container.id];
    }    
    
    
    def addContainer = { 		
		log.debug params 		
    	def shipment = Shipment.get(params.shipmentId);   	
    	def containerType = ContainerType.get(params.containerTypeId);    	
    	def containerName = (params.name) ? params.name : containerType.name + " " + (shipment.getContainers().size() + 1);
        def container = new Container(name: containerName, weight: params.weight, dimensions: params.dimensions, units: params.units, containerType: containerType);
        shipment.addToContainers(container);
        flash.message = "Added a new piece to the shipment";		
		redirect(action: 'editContents', id: params.shipmentId)    
    }

	/*
	def editContainer = {
		def container = Shipment.get(params.containerId);
		def containerType = ContainerType.get(params.containerTypeId);
		def containerName = (params.name) ? params.name : containerType.name + " " + shipment.getContainers().size()
		def container = new Container(name: containerName, weight: params.weight, units: params.units, dimensions: params.dimensions, containerType: containerType);
		container.save(flush:true);
		flash.message = "Added a new piece to the shipment";
		redirect(action: 'show', id: params.shipmentId)
	}*/

	
	def editContainer = {		
		def shipmentInstance = Shipment.get(params.shipmentId)		
		def containerInstance = Container.get(params.containerId)
		if (containerInstance) {
			/*
			if (params.version) {
				def version = params.version.toLong()
				if (containerInstance.version > version) {					
					containerInstance.errors.rejectValue("version", "default.optimistic.locking.failure", [message(code: 'container.label', default: 'Container')] as Object[], "Another user has updated this Product while you were editing")
					render(view: "edit", model: [containerInstance: containerInstance])
					return
				}
			}
			*/
			containerInstance.properties = params
			if (!containerInstance.hasErrors() && containerInstance.save(flush: true)) {
				flash.message = "${message(code: 'default.updated.message', args: [message(code: 'container.label', default: 'Container'), containerInstance.id])}"
				redirect(action: "showDetails", id: shipmentInstance.id)
			}
			else {
				flash.message = "Could not edit container"
				redirect(action: "showDetails", id: shipmentInstance.id)
				//render(view: "edit", model: [containerInstance: containerInstance])
			}
		}
		else {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'container.label', default: 'Container'), params.containerId])}"
			redirect(action: "showDetails", id: shipmentInstance.id)
			//redirect(action: "list")
		}
	}
	
	
	    
    def copyContainer = { 
    	def shipment = Shipment.get(params.shipmentId);   	
    	def container = Container.get(params.containerId);  
    	def name = (params.name) ? params.name : "New Package";
    	def copies = params.copies
    	def x = Integer.parseInt(copies)
    	int index = 1;
    	while ( x-- > 0 ) {
    		def containerCopy = new Container(container.properties);
    		containerCopy.id = null;
    		containerCopy.name = name + " " + (index++);
    		containerCopy.containerType = container.containerType;
    		containerCopy.weight = container.weight;
    		containerCopy.dimensions = container.dimensions;
    		containerCopy.shipmentItems = null;
    		containerCopy.save(flush:true);
    		
    		container.shipmentItems.each { 
    			def shipmentItemCopy = new ShipmentItem();
    			shipmentItemCopy.product = it.product
    			shipmentItemCopy.quantity = it.quantity;
    			containerCopy.addToShipmentItems(shipmentItemCopy).save(flush:true);
    		}
    		
    		shipment.addToContainers(containerCopy).save(flush:true);
    	}
		flash.message = "Copied package multiple times within the shipment";		
		redirect(action: 'show', id: params.shipmentId)        		
    }    
    
    
    def deleteContainer = { 
    		
		def container = Container.get(params.id);
    	def shipmentId = container.getShipment().getId();
    	
    	if (container.getShipmentItems().size() > 0) {
    		flash.message = "Cannot delete a container that is not empty";
    		redirect(action: 'show', id: shipmentId);    		
    	}
    	else { 
    		container.delete();	    	    	
    		redirect(action: 'show', id: shipmentId)     		
    	}    		
    }
    
    
    def addComment = { 
    	log.debug params;
    	def shipment = (params.shipmentId) ? Shipment.get(params.shipmentId) : null;    	
    	def recipient = (params.recipientId) ? User.get(params.recipientId) : null;
    	def comment = new Comment(comment: params.comment, commenter: session.user, recipient: recipient)
    	if (shipment) { 
	    	shipment.addToComments(comment).save();
	    	flash.message = "Added comment '${params.comment}'to shipment $shipment.id";		
    	}
		redirect(action: 'show', id: params.shipmentId)    	    		
    }
    
    def deleteComment = { 
    	def comment = Comment.get(params.id);
   		def shipmentId = comment.getShipment().getId();    	
    	if (comment) { 	    	
       	    comment.delete();	    	    	
        	flash.message = "Deleted comment $comment from shipment $shipment.id";		
	    	redirect(action: 'show', id: shipmentId) 
    	}
    	else { 
        	flash.message = "Could not remove comment $params.id from shipment";		
    		redirect(action: 'show', id: shipmentId)    	
    		
    	}
    }
    
    

    def addItem = {     		
    	log.debug params;		
		def container = Container.get(params.containerId);
    	def product = Product.get(params.productId);
    	def quantity = params.quantity;
    	// if container already includes a shipment item with this product, 
    	// we just need to add to the total quantity
    	def weight = product.weight * Integer.valueOf(quantity);
    	
		//def donor = null;
		//if (params.donorId)
		def donor = Organization.get(params.donorId);
					
		def shipmentItem = new ShipmentItem(product: product, quantity: quantity, weight: weight, donor: donor);
    	container.addToShipmentItems(shipmentItem).save(flush:true);
    	flash.message = "Added $params.quantity units of $product.name";		
		redirect(action: 'show', id: params.shipmentId)    	
    	
    }
	
	
	def addDocument = { 
		log.info params
		def shipmentInstance = Shipment.get(params.id);
		if (!shipmentInstance) {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'shipmentEvent.label', default: 'ShipmentEvent'), params.id])}"
			redirect(action: "list")
		}
		render(view: "addDocument", model: [shipmentInstance : shipmentInstance, document : new Document()]);
	}
    

    def deleteItem = { 
    	def item = ShipmentItem.get(params.id);
		def container = item.getContainer();
		def shipmentId = container.getShipment().getId();    	
    	if (item) { 	    	
	    	item.delete();	    	    	
        	flash.message = "Deleted shipment item $params.id from container $container.name";		
	    	redirect(action: 'show', id: shipmentId) 
    	}
    	else { 
        	flash.message = "Could not remove item $params.id from container";		
    		redirect(action: 'show', id: shipmentId)    	
    		
    	}
    }

    
    def deleteDocument = { 
    	def document = Document.get(params.id);
   		def shipmentId = document.getShipment().getId();    	
    	if (document) { 	    	
       	    document.delete();	    	    	
        	flash.message = "Deleted document $params.id from shipment";		
	    	redirect(action: 'show', id: shipmentId) 
    	}
    	else { 
        	flash.message = "Could not remove document $params.id from shipment";		
    		redirect(action: 'show', id: shipmentId)    	
    		
    	}
    }
    
    
    def addEvent = { 
		
		
    	if ("GET".equals(request.getMethod())) { 
			
			def shipmentInstance = Shipment.get(params.id);
			if (!shipmentInstance) {
				flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'shipmentEvent.label', default: 'ShipmentEvent'), params.id])}"
				redirect(action: "list")
			}
			render(view: "addEvent", model: [shipmentInstance : shipmentInstance, shipmentEvent : new ShipmentEvent()]);
		
			
		}
		else { 
			
	    	def targetLocation = null    	
	    	if (params.targetLocationId) { 
	        	Location.get(params.targetLocationId)
	    	}
			
	    	ShipmentEvent event = new ShipmentEvent(
	    		eventType:EventType.get(params.eventTypeId), 
	    		eventDate: params.eventDate, 
	    		eventLocation: Location.get(params.eventLocationId),
	    		targetLocation: targetLocation
	    	);
	    	
	    	def shipment = Shipment.get(params.shipmentId);     	
	    	shipment.addToEvents(event).save(flush:true);    
	
	    	flash.message = "Added event";		
			redirect(action: 'showDetails', id: params.shipmentId)    	
		}
	}    

    def deleteEvent = { 
    	def event = Event.get(params.id);
    	def shipmentId = event.getShipment().getId();    	
    	event.delete();	    	    	
    	redirect(action: 'show', id: shipmentId) 
    }
    
	
	def addReferenceNumber = { 		
		def referenceNumber = new ReferenceNumber(params);
		def shipment = Shipment.get(params.shipmentId);
		shipment.addToReferenceNumbers(referenceNumber);
		flash.message = "Added reference number";
		redirect(action: 'show', id: params.shipmentId)
	}
    
    def form = {
        [ shipments : Shipment.list() ]
    }
    
    def view = {
    	// pass through to "view shipment" page
    }
}


class ListCommand { 	
	String category;
	String color;
	List objectList;
	Integer sortOrder;

    static constraints = {

    }
	
	
	public int compareTo(def other) {
		return id <=> other?.id
		
		//return sortOrder <=> other?.sortOrder // <=> is the compareTo operator in groovy
	}
	

	
}

