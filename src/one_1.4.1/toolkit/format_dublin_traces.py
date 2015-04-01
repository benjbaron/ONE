#!/usr/bin/python
# -*- coding: utf8 -*-

## This file formats the input GPS trace from Dublin City Council 
## http://dublinked.com/datastore/datasets/dataset-304.php
## for the ONE simulator (http://www.netlab.tkk.fi/tutkimus/dtn/theone/)

## Formatting constraints given by the ONE simulator
#  - All lines must be sorted by time
#  - Sampling interval (time difference between two time instances) must be same for the whole file

### Format of the lines
## First line of the file should be the offset header. Syntax of the header should be:
# minTime maxTime minX maxX minY maxY minZ maxZ
# Last two values (Z-axis) are ignored at the moment but can be present in the file
#
## Following lines' syntax should be:
# time id xPos yPos
# where "time" is the time when a node with "id" should be at location "(xPos, yPos)"
from __future__ import division

import os, sys
from pyproj import Proj
from shapely.geometry import Point, LineString
import csv
from bisect import bisect_left
from collections import OrderedDict
import operator
import time
import numpy
import math

proj = Proj(proj='utm',zone=29,ellps='GRS80') 
DATA_DIR = "/Users/ben/Data/Dublin-buses-traces-2013/"
TRACE_FILE = DATA_DIR + 'siri.20130129.csv'
GEO_INF = 1e10

class Waypoint:
	def __init__(self, lon, lat, ts=0):
		self.lon = float(lon)
		self.lat = float(lat)
		self.timestamp = int(ts)
	def __str__(self):
		return "(%.10f,%.10f,%d)" % (self.lon, self.lat, self.timestamp,)
	
	def distance(self, point):
		""" Returns the Euclidean distance between this point and "point" in meters. """
		a = numpy.array((self.lon, self.lat))
		b = numpy.array((point.lon, point.lat))
		return numpy.linalg.norm(a-b)

class Node:
	def __init__(self, id, trajectory=None):
		self.id = id
		if trajectory: # Deep-copy it
			self._trajectory = copy.deepcopy(trajectory) # Will return {} if trajectory is {}
		else:
			self._trajectory = OrderedDict()

	def __getitem__(self, index):
		return self._trajectory[index]
	def __setitem__(self, index, value):
		self._trajectory[index] = value
	def __len__(self):
		return len(self._trajectory)
	def keys(self):
		return self._trajectory.keys()
	def values(self):
		return self._trajectory.values()
	def items(self):
		return self._trajectory.items()
	def start_time(self):
		return self._trajectory.keys()[0]
	def end_time(self):
		return self._trajectory.keys()[-1]

	def is_active(self, t):
		""" Returns true if the node is active at timestamp t, false otherwise """
		if t < self.start_time() or t > self.end_time():
			return False
		return True

	def sort(self):
		""" Sort the _trajectory dictionary """
		self._trajectory = OrderedDict(sorted(self._trajectory.items(), key=operator.itemgetter(0)))
	
	def position(self, t):
		""" Returns the Waypoint position of the node at time ts using a linear interpolation
			If the position cannot exist, then return None """

		if t in self._trajectory.keys():
			return self._trajectory[t]

		if not self.is_active(t):
			return None

		pos = bisect_left(self._trajectory.keys(), t)
		t1 = self._trajectory.keys()[pos-1]
		t2 = self._trajectory.keys()[pos]
		
		wp1,wp2 = self._trajectory[t1], self._trajectory[t2]
		speed = wp1.distance(wp2) / ((t2-t1) / 1e6)
		if speed > 30.0: return None
		time_diff = (t2 - t1) / 1e6 # Time difference between the two timestamps in seconds
		# The position is invalid if there has not been a waypoint for 10 consecutive minutes
		if time_diff > 600.0: return None 
		
		x1,y1 = wp1.lat,wp1.lon
		x2,y2 = wp2.lat,wp2.lon
		x = x1 + (t-t1)/(t2-t1) * (x2-x1)
		y = y1 + (t-t1)/(t2-t1) * (y2-y1)

		return Waypoint(y,x,t) # Waypoint(lon, lat, timestamp)

	def distance(self, t, node):
		""" Returns the Euclidean distance between the current node and the node passed as an argument at time t """
		pos1 = self.position(t)
		pos2 = node.position(t)
		
		if pos1 and pos2:
			return pos1.distance(pos2)
		return None


def readTraceFile(trace_file, trace_file_header=False):
	""" Populates and returns the "trajectories" dictionary from the GPS trace "trace_file" file """
	
	nodes = OrderedDict() # The nodes indexed by their id (here: bus_id)

	# Instanciate the trajectories and populate the "_waypoints" dictionary
	with open(trace_file) as f_trace:
		reader_trace = csv.reader(f_trace)
		
		if trace_file_header:
			next(reader_trace, None) # skip the header
		
		for traceLine in reader_trace:
			timestamp,route_id,direction,journey_pattern_id,time_frame,trip_id,operator,congestion,lon,lat,delay,block_id,bus_id,stop_id,at_stop = traceLine

			if bus_id not in nodes:
				# Instanciate the node
				nodes[bus_id] = Node(bus_id)
			proj_lon, proj_lat = proj(float(lon), float(lat))
			nodes[bus_id][int(timestamp)] = Waypoint(proj_lon, proj_lat, int(timestamp))

	# Sort the trajectories of the instanced nodes
	for node in nodes.values():
		node.sort()

	return nodes

def outputTraceFile(nodes, output_file, sample_int=1e6):
	""" Writes the traces of "nodes" in "output_file" at each sampling interval defined by "sample_int"
		The  output trace file follows the format as defined by the ONE simulator """

	# get the start of the trace
	start_time = min([node.start_time() for node_id, node in nodes.items()])
	end_time = int(start_time + 3600*1e6)# max([node.end_time() for node_id, node in nodes.items()])
	minTime = 0
	maxTime = int((end_time - start_time) / sample_int)

	print start_time, end_time, minTime, maxTime, sample_int

	minX, maxX, minY, maxY = GEO_INF, -GEO_INF, GEO_INF, -GEO_INF
	
	fname, fext = os.path.splitext(output_file)
	filename = fname + "_" + str(int(sample_int)) + fext

	with open(filename, 'w') as f:
		active_nodes = dict()

		for current_time in range(minTime, maxTime+1):
			ts = start_time + current_time * sample_int
			for node_id, node in nodes.items():
				pos = node.position(ts)
				if pos:
					state = 'UP'
					active_nodes[node_id] = pos
					xPos, yPos = pos.lon, pos.lat
					# Line format is "time id xPos yPos state"
					f.write("%d %s %.6f %.6f %s\n" % (int(current_time), node_id, xPos, yPos, state,))
					
					# Update the bounding box attributes
					if xPos < minX: minX = xPos
					if xPos > maxX: maxX = xPos
					if yPos < minY: minY = yPos
					if yPos > maxY: maxY = yPos
				elif (node_id in active_nodes):
					state = 'DOWN'
					end_pos = active_nodes[node_id]
					xPos, yPos = end_pos.lon, end_pos.lat
					print "%d %s %.6f %.6f %s" % (int(current_time), node_id, xPos, yPos, state,)
					f.write("%d %s %.6f %.6f %s\n" % (int(current_time), node_id, xPos, yPos, state,))
					del active_nodes[node_id] # node_id is not active anymore


			print int(ts), int(current_time), len(active_nodes)

	# round up values bounds to the next multiple of 500
	minX = math.floor(minX / 500.0) * 500.0
	maxX = math.ceil(maxX / 500.0) * 500.0
	minY = math.floor(minY / 500.0) * 500.0
	maxY = math.ceil(maxY / 500.0) * 500.0

	# Open the file agin to write the first line
	f = open(filename, 'r')
	text = f.read()
	f.close()
	# open the file again for writing
	f = open(filename, 'w')
	# Write the first line
	f.write("%d %d %d %d %d %d\n" % (minTime, maxTime, minX, maxX, minY, maxY))
	# Write the initialisation of all of the nodes (for the init phase)
	for node_id, node in nodes.items():
		ts = start_time + minTime * sample_int
		pos = node.position(ts)
		if pos:
			state = 'UP'
			xPos, yPos = pos.lon, pos.lat
			f.write("%d %s %.6f %.6f %s\n" % (int(minTime), node_id, xPos, yPos, state))
		else:
			state = 'DOWN'
			init_pos = node.position(node.start_time())
			xPos, yPos = init_pos.lon, init_pos.lat
			f.write("%d %s %.6f %.6f %s\n" % (int(minTime), node_id, xPos, yPos, state))
	# write the original contents
	f.write(text)
	f.close()

	print minTime, maxTime, minX, maxX, minY, maxY, (maxX-minX), (maxY-minY)


if __name__ == '__main__':
	startTime = time.clock()
	print "Step#1 Loading the GPS traces"
	# nodes = readTraceFile(TRACE_FILE)
	sys.stdout.flush()
	sys.stderr.write("Completed in {} seconds.\n".format(time.clock() - startTime))

	ofile = "output_file.txt"
	print "Step#2 Ouput the GPS traces in " + ofile
	outputTraceFile(nodes, ofile)
