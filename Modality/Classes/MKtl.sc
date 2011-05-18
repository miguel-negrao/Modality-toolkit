// honouring Jeff's MKeys by keeping the M for prototyping the new Ktl


// TODO:
//	default deviceDescription files in quarks, custom ones in userAppSupportDir
//		(Platform.userAppSupportDir +/+ "MKtlSpecs").standardizePath, 
//		if (deviceDescriptionFolders[0].pathMatch.isEmpty) { 
//			unixCmd("mkdir \"" ++ deviceDescriptionFolder ++ "\"");
//		};

MKtl { // abstract class
	classvar <deviceDescriptionFolder;
	classvar <allDevDescs;
	classvar <all; // will hold all instances of MKtl
	classvar <specs; // all specs

	var <name;	// a user-given unique name
	
	// an array of keys and values with a description of all the elements on the device.
	// is read in from an external file.
	var <deviceDescription; 

	// all control elements (MKtlElement) on the device you may want to listen or talk to
	var <elements;

	var <responders;

	//var <>recordFunc; // what to do to record incoming control changes
	
	*initClass {
		all = ();
		
		specs = ().parent_(Spec.specs);

		// general
		this.addSpec(\cent255, [0, 255, \lin, 1, 128]);
		this.addSpec(\cent255inv, [255, 0, \lin, 1, 128]);
		this.addSpec(\lin255,  [0, 255, \lin, 1, 0]);

		// MIDI
		this.addSpec(\midiCC, [0, 127, \lin, 1, 0]); 
		this.addSpec(\midiVel, [0, 127, \lin, 1, 0]); 
		this.addSpec(\midiBut, [0, 127, \lin, 127, 0]); 
		this.addSpec(\compass8, [0, 8, \lin, 1, 1]); // may be wrong, check again!

		// HID
		this.addSpec(\hidBut, [0, 1, \lin, 1, 0]);
		this.addSpec(\hidHat, [0, 1, \lin, 1, 0]);
		this.addSpec(\compass8, [0, 8, \lin, 1, 1]); // probably wrong, check again!

		deviceDescriptionFolder = this.filenameSymbol.asString.dirname.dirname +/+ "MKtlSpecs";
	}

	*find {
		this.allSubclasses.do(_.find);	
	}

	*addSpec {|key, spec|
		specs.put(key, spec.asSpec);	
	}

	*makeShortName {|deviceID|
		^(deviceID.asString.toLower.select{|c| c.isAlpha && { c.isVowel.not }}.keep(4))
	}
	
		// new returns existing instances
		// of subclasses that exist in .all, 
		// or returns a new empty instance. 
		// this is to allow virtual MKtls eventually.
	*new { |name, deviceDescName|
		var devDesc;
		if (deviceDescName.isNil) { ^all[name] };

		// create an instance of the right subclass based on the protocol given in the device description
		devDesc = this.getDeviceDescription( deviceDescName );
		devDesc[ \protocol ].switch(
			\midi, ^MIDIMKtl.newFromDesc( name, deviceDescName, devDesc ),
			\hid, ^HIDMKtl.newFromDesc( name, deviceDescName, devDesc )
			//\osc, ^OSCMKtl.new( name, deviceDesc ),
			//\serial, ^SerialMKtl.new(name, deviceDesc )
		);

		^this.basicNew(name, deviceDescName);
	}
	
	*basicNew { |name, deviceDescName| 
		^super.new.init(name, deviceDescName);
	}

	*make { |name, deviceDescName|
		if (all[name].notNil) {
			warn("MKtl name '%' is in use already. Please use another name."
				.format(name));
			^nil
		};
		
		^this.basicNew(name, deviceDescName);
	}
	
	init { |argName, deviceDescName|
		name = argName; 
		
		//envir = ();
		elements = ();
		if (deviceDescName.isNil) { 
			warn("no deviceDescription name given!");
		} {
			this.loadDeviceDescription(deviceDescName);
			this.makeElements;
		};
		all.put(name, this);
	}
	
	storeArgs { ^[name] }
	printOn { |stream| this.storeOn(stream) }

	*loadDeviceIndex{ |reload=false|
		var path;
		if ( allDevDescs.isNil or: reload ){
			path = deviceDescriptionFolder +/+ "index.desc.scd";
			allDevDescs = try { 
				path.load;
			} { 
				"//" + this.class ++ ": - no device description index found!\n"
				.post;
			};
		};		
	}

	// this tries to find a description from an os specifically given device name
	findDescriptions{ |rawDeviceName|
		var found = List.new;
		this.loadDeviceIndex;
		allDevDescs.keyValuesDo{ |key,desc|
			if ( desc[ thisProcess.platform.name ] == rawDeviceName ){
				found.add( key );
			};
		};
		^found;
	}

	*getDeviceDescription{ |devName|
		this.loadDeviceIndex;
		^allDevDescs.at( devName );
	}

	// not sure if we need this method anymore, but maybe useful for automatic saving of a user defined description?
	*getCleanDeviceName{ |dirtyName|
		var cleanDeviceName = dirtyName.collect { |char| if (char.isAlphaNum, char, $_) };
		^cleanDeviceName;
	}

	// this takes the actual filename
	loadDeviceDescription { |deviceName| 
		var deviceInfo;
		var deviceFileName;
		var path;

		// look the filename up in the index
		deviceInfo = this.class.getDeviceDescription( deviceName );
		deviceInfo.postln;
		// deviceInfo also has information about protocol and os specific naming

		deviceFileName = deviceInfo[ \file ];

		path = deviceDescriptionFolder +/+ deviceFileName;
		path.postln;

		deviceDescription = try { 
			path.load;
		} { 
			"//" + this.class ++ ": - no device description found for %: please make them!\n"
				.postf(deviceName);
		//	this.class.openTester(this);
		};

		// create specs
		deviceDescription.pairsDo {|key, elem| 
			var foundSpec =  specs[elem[\spec]];
			if (foundSpec.isNil) { 
				warn("Mktl - in description %, spec for '%' is missing! please add it with:"
					"\nMktl.addSpec( '%', [min, max, warp, step, default]);\n"
					.format(deviceName, elem[\spec], elem[\spec])
				);
			};
			elem[\specName] = elem[\spec];
			elem[\spec] = this.class.specs[elem[\specName]];
		};
	}
	
	*postAllDescriptions {
		(MKtl.deviceDescriptionFolder +/+ "*").pathMatch
			.collect { |path| path.basename.splitext.first }
			.reject(_.beginsWith("_"))
			.do { |path| ("['" ++ path ++"']").postln }
	}

	deviceDescriptionFor { |elname| ^deviceDescription[deviceDescription.indexOf(elname) + 1] }

	postDeviceDescription {
		deviceDescription.pairsDo {|a, b| "% : %\n".postf(a, b); }
	}

	makeElements {
		this.elementNames.do{|key|
			elements[key] = MKtlElement(this, key);
		}
	}
	
		// convenience methods
	defaultValueFor { |elName|
		^this.elements[elName].defaultValue
	}
	
	elementNames { 
		^(0, 2 .. deviceDescription.size - 2).collect (deviceDescription[_])
	}
	
	elementsOfType { |type|
		^elements.select{ |elem|
   			elem.deviceDescription[\type] == type
		}	
	}
	
	// element funcChain interface
	addFunc { |elementKey, funcName, function, addAction, otherName|
		elements[elementKey].addFunc(funcName, function, addAction, otherName);
	}

	addFuncAfter { |elementKey, funcName, function, otherName|
		elements[elementKey].addFuncAfter(funcName, function, otherName);
	}
	
	addFuncBefore { |elementKey, funcName, function, otherName|
		elements[elementKey].addFuncBefore(funcName, function, otherName);
	}
	
	removeFunc { |elementKey, funcName| 		
		elements.do{ |elem|
			var key = elem.name;
			if( key.matchOSCAddressPattern(elementKey) ) {
				elements[key].removeFunc(funcName);
			}
		}
	}

	// interface compatibility for make MKtl usable like a Dispatch (sometimes called duck-typing (tm))
	// allow to register easilly to multiple elements:
	//i.e.  'sl*'
	//i.e.  'sl1_?'
	//i.e.  '*'
	addToOutput { |elementKey, funcName, function, addAction, otherName|
		elements.do{ |elem|
			var key = elem.name;
			if( key.matchOSCAddressPattern(elementKey) ) {
				this.addFunc(key, funcName, function, addAction, otherName)
			}
		}
	}
	
	removeFromOutput{ |elementKey, funcName| 
		this.removeFunc(elementKey, funcName)
	}

	// remove all functionalities from all elements
	reset {
		elements.do(_.reset)
	}
	
	recordValue { |key,value|
//		recordFunc.value( key, value );
	}

	
	//useful if Dispatcher also uses this class
	//also can be used to simulate a non present hardware
	receive { |key, val|
		// is it really inputs ?
		elements[ key ].update( val )
	}
	
	send { |key, val|
			
	}


}

MKtlBasicElement {
	
	var <source; // the Ktl it belongs to
	var <name; // its name in Ktl.elements
	var <type; // its type. 

	// Note to devs: 
	//	Do never ever replace this funcChain with a new instance. 
	// 	It is referenced externally for optimization (e.g. in MIDIKtl)
	var <funcChain; 	
	
	// keep value and previous value here
	var <value;
	var <prevValue;

	*new { |source, name|
		^super.newCopyArgs( source, name).init;
	}

	init { 
		funcChain = FuncChain.new;
	}

		// remove all functionalities from the funcChains
	reset {
		this.init;
	}

	// funcChain interface //
	
	// by default, just do add = addLast, no flag needed. 
	// (indirection with perform is much slower than method calls.)
	addFunc { |funcName, function, addAction, otherName|
		// by default adds the action to the end of the list
		// if otherName is set, the valid addActions are: 
		// \addLast, \addFirst, \addBefore, \addAfter, \replaceAt, are valid
		funcChain.add(funcName, function, addAction, otherName);
	}

	addFuncAfter { |funcName, function, otherName|
		funcChain.addAfter(funcName, function, otherName);
	}
	
	addFuncBefore { |funcName, function, otherName|
		funcChain.addBefore(funcName, function, otherName);
	}
	
	replaceFunc { |funcName, function, otherName| 
		funcChain.replaceAt(funcName, function, otherName);
	}
	
	removeFunc {|funcName| 
		funcChain.removeAt(funcName) 
	}
	
	send { |val|
		value = val;
		//then send to hardware 	
	}

	value_ { | newval |
		// copies the current state to:
		prevValue = value;
		// updates the state with the latest value
		value = newval;
	}

	valueAction_ { |newval|
		this.value_( newval );
		source.recordValue( name, newval );
		//funcChain.value( name, newval );
		funcChain.value( this );
	}
	
	doAction {
		funcChain.value( this );
	}

}

MKtlElement : MKtlBasicElement{
	classvar <types;
		
	var <deviceDescription;	 // its particular device description  
	var <spec; // its spec

	*initClass {
		types = (
			\slider: \x,
			\button: \x,
			\thumbStick: [\joyAxis, \joyAxis, \button],
			\joyStick: [\joyAxis, \joyAxis, \button]
		)
	}

	*new { |source, name|
		^super.newCopyArgs( source, name).init;
	}

	init { 
		super.init;
		deviceDescription = source.deviceDescriptionFor(name);
		spec = deviceDescription[\spec];
		if (spec.isNil) { 
			warn("spec for '%' is missing!".format(spec));
		} { 
			value = prevValue = spec.default ? 0;
		};
		type = deviceDescription[\type];

		spec = deviceDescription[\spec];
		if (spec.isNil) { 
			warn("spec for '%' is missing!".format(spec));
		} { 
			value = prevValue = spec.default ? 0;
		};
	}

	defaultValue {
		^spec.default;	
	}
	
	value { ^spec.unmap(value) }
	
	rawValue { ^value }
}