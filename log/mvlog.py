#!/usr/bin/env python

from __future__ import with_statement
from pychart import *
from datetime import *
import string
import re, sys
import pdb

"""
This class used to draw sensor and user event /data/mv.log using pychart.
http://home.gna.org/pychart/doc/module-chart-data.html
Due to the --format=pdf command line option, I could not pass log file to sys.argv.
I have to hard code it.

Using bar plot. data =[(tm, user, sensor), (), ... ]
1. Moving y val = 100, station y val = 30. hide y val = 0 
2. when user state, point = (tm, user, 0)
3. when sensor dete, point =(tm, 0, sensor)
4. bar plot with diff color for user/sensor

usage: python mvlog.py --format=pdf > loc.pdf
"""

class MvLog():
    # do we have any class variable ?

    def __init__(self):
        self.stime = None
        self.etime = None
        self.userevent = []
        self.sensorevent = []
        self.timeline = []
		# data is 3 val tuple. [(tm, user, sensor),(),...] 
        self.dataset = []  
        # the start time and end time
        self.xstart = 0 
        self.xend = 0
        self.latlngaccu = []

    def log(self, data):
        #for t in data:
        #    print t
        pass
                    
    def stateVal(self, state):
        if state.lower() == 'moving':
            return 100
        elif state.lower() == 'stationary' or state.lower() == 'station':
            return 30
        else:
            return 0
        

    def getData(self, file):
        with open(file) as f:
            for line in f:
                el = line.split()

                if len(el) < 2 or not ( re.search('sensor', line)):
                    continue
                
                day = el[0][5:]
                tm = el[1]
                evtm = day + ' ' + tm
                #print evtm
                
				# process set by user
                if el[6] == 'set':
					t = evtm, self.stateVal(el[5]), 0
					self.dataset.append(t)
					if el[5] == el[3]:  # if sensor already detected
						t = evtm, 0, self.stateVal(el[3])
						self.dataset.append(t)
					continue  # done with this line

                # first, skip monitoring
                if not self.stateVal(el[3]):
                    continue

				# process sensor event
                if el[6] != 'set':
					t = evtm, 0, self.stateVal(el[3])
					self.dataset.append(t)
					continue
    
        self.log(self.dataset)
        return self.dataset

    def makechart(self, data):
            def interval_to_time(m):
                #curtime = datetime.now()
                return m

            def format_time(m):
                startmin = int(self.stime.split(':')[1])
                hour = m/60
                min = m%60 + startmin
                if min > 60:
                    hour += 1
                    min = min%60
                #print self.xstart, ':', hour, '::', min
                if min < 10:
                    return "/a60/7{}" + str(self.xstart+hour)+':0'+str(min)
                else:
                    return "/a60/7{}" + str(self.xstart+hour)+':'+str(min)
            
            def formatY(x):
                if x == 100:
                    return "/a90/7{}"+"Moving"
                elif x == 30:
                    return "/a90/7{}"+"Station"
                else:
                    return "/a90"
                
            theme.get_options()
            chart_object.set_defaults(area.T,  x_coord = category_coord.T(data, 0))
            chart_object.set_defaults(bar_plot.T, data=data)

            tick1 = tick_mark.Circle(size=2, fill_style=fill_style.black)
            tick2 = tick_mark.Circle(size=4, fill_style=fill_style.black)

            # format="/angel90/fontsize{}%s"
            #xaxis = axis.X(label="Time", format="/a-60{}%d", tic_interval=20)
            xaxis = axis.X(label="Time", format="/a80/5{}%s")
            yaxis = axis.Y(label="Movement", format=formatY)

            # data is an array of tuple(tm, user, sensor) x[0] == time
            #data = chart_data.transform(lambda x: [interval_to_time(x[0]), x[1]], data)

            #ar = area.T(x_axis=xaxis, y_axis=yaxis,y_coord=log_coord.T(), y_range=(0,None),
            ar = area.T(x_axis=xaxis, y_axis=yaxis, 
                        x_coord=category_coord.T(data, 0), 
                        x_grid_interval=10, x_grid_style=line_style.gray70_dash3,
                        size=(360, 110),
                        legend = legend.T(loc=(300, 100)), 
                        loc = (0, 0))

            title = 'Movement Detection'
            #ar.add_plot(line_plot.T(label="/8"+title, data=data,tick_mark=tick1))
            ar.add_plot(bar_plot.T(label="Actual", hcol=1, cluster=(0, 2), width=2, fill_style = fill_style.red),
                        bar_plot.T(label="Sensor", hcol=2, cluster=(1, 2), width=2))
            #ar.add_plot(line_plot.T(label="LocationUpdate", data=[(10,20), (20,30), (30, 40)], tick_mark=tick2))
            #ar.add_plot(line_plot.T(label="LocationUpdate", data=randomdata(), tick_mark=tick2))
            ar.draw()

if __name__ == '__main__':
    if len(sys.argv) < 2:
        #print "Usage: python mvlog.py logfile"
        #sys.exit()
        pass

    mvlog = MvLog()
    data = mvlog.getData('mv.log')
    #data = locs.getData(sys.argv[1])
    mvlog.makechart(data)
