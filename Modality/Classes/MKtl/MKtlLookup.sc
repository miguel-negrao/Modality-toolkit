// TODO: check that we never add identical infos a second time

MKtlLookup {
	classvar <all, <midiAll;

	classvar orderedInfoKeys =
	#[\idInfo, \protocol, \lookupName, \filename, \deviceInfo, \srcDevice, \destDevice];

	*initClass {
		all = ();
		midiAll = ();
	}

	*names {
		^all.keys(SortedList).array;
	}

	*allFor { |protocol|
		protocol = (protocol ? MKtlDevice.allProtocols);
		^all.select { |dict|
			(dict.protocol == protocol) or: {
				protocol.asArray.includes(dict.protocol);
			}
		};
	}

	*postInfo {
		"%.all: \n".postf(this);
		all.sortedKeysValuesDo { |devkey, devdict|
			var extraInfoKeys;
			"\ % : \n".postf(devkey.cs);

			extraInfoKeys = devdict.keys(Array).removeAll(orderedInfoKeys);
			(orderedInfoKeys ++ extraInfoKeys.sort).do { |k|
				"\t  %  %\n".postf((k.asString ++ ":").padRight(10), devdict[k].cs)
			};
			"   ---".postln;
		};
	}

	*addAllHID {
		HIDMKtlDevice.devicesToShow.sortedKeysValuesDo { |index, info|
			MKtlLookup.addHID(info, index);
		};
	}

	*addHID { | hidinfo, index |
		var protocol = \hid;
		var lookupName = MKtl.makeLookupName(\hid, index, hidinfo.productName);
		var idInfo = [hidinfo.productName, hidinfo.vendorName].join($_);
		var filename = MKtlDesc.filenameForIDInfo(idInfo);

		var dict = (
			protocol: \hid,
			idInfo: idInfo,
			deviceInfo: hidinfo,
			filename: filename,
			lookupName: lookupName
		);

		if (filename.notNil) {
			MKtlDesc.loadDescs(filename);
			dict.put(\desc, MKtlDesc.at(filename.asSymbol));
		};

		all.put(lookupName, dict);
		^dict
	}

	*addAllMIDI {
		// clear first to avoid buildup
		MKtlLookup.allFor(\midi).keysDo { |key| MKtlLookup.all.removeAt(key) };

		// join the ones with the same idInfo first,
		// and collect all their srcIDs/destIDs:
		MIDIClient.sources.do { |endpoint, index|
			MKtlLookup.addOrMergeMIDI(endpoint, index, \src);
		};
		MIDIClient.destinations.do { |endpoint, index|
			MKtlLookup.addOrMergeMIDI(endpoint, index, \dest);
		};
		// need to know all available devices in the big list first,
		// then can sort out which ones belong together
		midiAll.do { |dev|
			this.midiSplit(dev);
		}
	}

	*addOrMergeMIDI { |endpoint, index, endPointType|
		var infoToMergeTo = MKtlLookup.midiAll.detect { |info|
			info.idInfo == endpoint.device
		};
		// "%: %\n".postf([endpoint, index, endPointType]);
		if (infoToMergeTo.isNil) {
			^MKtlLookup.addMIDI(endpoint, index, endPointType, midiAll);
		};

		if (endPointType == \src) {
			this.merge (infoToMergeTo, \srcDevice, endpoint);
		};
		if (endPointType == \dest) {
			this.merge (infoToMergeTo, \destDevice, endpoint);
		};
	}

	// for devices with multiple ports, for now:
	// merge all ports into one array for src and one for desc.
	// will need to unpack it correctly later:
	// either support multiple ports in MIDIfunc,
	// or merge one MKtl for each port into one mother MKtl

	*merge { |dict, key, newItem|
		var arr = dict[key].asArray;
		if (arr.includesEqual(newItem).not) {
			// no array for single ports
			arr = arr.add(newItem).unbubble;
			dict[key] = arr;
		};
	}

	*addMIDI { |endPoint, index, endPointType = \src, where, lookupName|

		var protocol = \midi;
		var idInfo = endPoint.device;
		var filename = MKtlDesc.filenameForIDInfo(idInfo);
		var dict;
		lookupName = lookupName ?? {
			MKtl.makeLookupName(protocol, index, endPoint.device);
		};

		dict = (
			protocol: protocol,
			idInfo: idInfo,
			filename: filename,
			desc: MKtlDesc.at(filename.asSymbol),
			lookupName: lookupName
		);
		dict.put(\deviceInfo, endPoint);
		if (endPointType == \src) { dict.put(\srcDevice, endPoint) };
		if (endPointType == \dest) { dict.put(\destDevice, endPoint) };

		(where ? all).put(lookupName, dict);

		^dict
	}

	*midiSplit { |info|

		var numSources, numDests, insOutsMatch, doAdvise;
		var numInPorts, numOutPorts, numInDevices, numOutDevices;
		var deviceName, postfix;
		var count = this.allFor(\midi).size;

		numSources = info.srcDevice.asArray.size;
		numDests =  info.destDevice.asArray.size;

		// if single device, exit here!
		if ((numSources < 2) and: { numDests < 2 }) {
			// "\nMKtlLookup: single midi device -> to all: %\n\n".postf(info);
			all.put(info.lookupName, info);
			^this
		};

		// does info have same number of srcs and dests?
		// -> if yes, assume same order on ins and outs!
		insOutsMatch = numSources == numDests;
		numInPorts = info.srcDevice.collectAs(_.name, Set).size;
		numOutPorts = info.destDevice.collectAs(_.name, Set).size;
		numInDevices = numSources / numInPorts;
		numOutDevices = numDests / numOutPorts;

		// "% numInPorts: %, numOutPorts: %, numInDevs: %, numOutDevs: %\n"
		// .postf(info.lookupName, numInPorts, numOutPorts, numInDevices, numOutDevices);

		info.srcDevice.do { |srcdev, index|
			var index1 = index + 1;
			info.sourcePortIndex = index;
			deviceName = "midi_%_%%"; postfix = "";
			if (numInDevices > 1) { postfix = postfix ++ "_nr_%".format(index1) };
			if (numInPorts > 1) { postfix = postfix ++ "_port_%".format(index1) };
			// [srcdev, index1, postfix].postln;
			deviceName = deviceName.copy
			.format(count + index1, info.idInfo.toLower, postfix)
			.collect { |char| if (char.isAlphaNum, char, $_) }
			.asSymbol;
			this.addMIDI(srcdev, count + index1, \src, lookupName: deviceName);
			if (insOutsMatch) {
				all[deviceName].destDevice = info.destDevice.asArray[index];
				info.destPortIndex = index;
				info.postcs;
			};
		};
		if (insOutsMatch.not) {
			info.destDevice.do { |destdev, index|
				var index1 = index + 1;
				info.destPortIndex = index;
				deviceName = "midi_%_%%"; postfix = "";
				if (numInDevices > 1) { postfix = postfix ++ "_devc_%".format(index1) };
				if (numInPorts > 1) { postfix = postfix ++ "_port_%".format(index1) };
				// [destdev, index1, postfix].postln;
				deviceName = deviceName.copy
				.format(count + index1, info.idInfo.toLower, postfix)
				.collect { |char| if (char.isAlphaNum, char, $_) }
				.asSymbol;
				this.addMIDI(destdev, count + index1, \dest, lookupName: deviceName);
			};
		};
	}

	// check if already there before adding
	// should be serialisable to write to file
	*addOSC { |sendAddr, name, replyAddr|

		var protocol = \osc;
		var idInfo = [sendAddr.addr, sendAddr.port].join($_);
			// maybe needed, but makes it hard to avoid duplicates
		// var index = MKtlLookup.all.count { |dict|
		// (dict.protocol == \osc) };
		// var nameAndInfo = if (name.notNil) { [name.asString, idInfo].join($_); };
		// var lookupName = MKtl.makeLookupName(protocol, index, nameAndInfo ? idInfo);
		var lookupName = MKtl.makeLookupName(protocol, name, idInfo);
		var filename = MKtlDesc.filenameForIDInfo(idInfo);

		var dict = (
			name: name,
			protocol: protocol,
			ipAddress: sendAddr.ip,
			srcPort: sendAddr.port,
			destPort: (replyAddr ? sendAddr).port,
		//	deviceInfo: nil,
			filename: filename,
			desc: MKtlDesc.at(filename.asSymbol),
			lookupName: lookupName
		//	lookup: { MKtlLookup.all[lookupName]; }
		);

		all.put(lookupName, dict);
		^dict
	}

	/*
	how to remove a deviceInfo for a given device?
	likely useful for OSC devices
	*/

	*addSerial {

	}

	*findByIDInfo { |inIdInfo|
		var matches = true, res;

		var inIdDict;
		if (inIdInfo.isKindOf(String)) {
			inIdDict = (deviceName: inIdInfo);
		} {
			inIdDict = inIdInfo
		};

		// inIdDict.postcs;

		res = all.select { |hereInfo|
			var hereIdInfo, hereIdDict, matches = true;

			hereIdInfo = hereInfo[\idInfo];
			if (hereIdInfo.isKindOf(String)) {
				hereIdDict = (deviceName: hereIdInfo);
			} {
				hereIdDict = hereIdInfo;
			};

			inIdDict.keysValuesDo { |key, value|
				var hereIDval = hereIdDict[key];
			//	[key, value, hereIDval].postcs;
				matches = matches and:
				((value == hereIDval) or: hereIDval.isNil)
			};
			matches
		};
		^res
	}
}

