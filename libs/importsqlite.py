#!/usr/bin/env python
from __future__ import with_statement
import sys, re
import sqlite3
import pdb

class LocDB:
    loctimecols = 'Lat,Lgt,StartTime,EndTime,Name,Count,Accuracy,BestAccuracy,Accuname,Poitag,CellJsonValue' 
    poicols = 'poi,lat,lgt,radius,addr,Name,poitype,celljsons,wifimac,wificonnmac,wifissid,btmac,time' 
    
    def __init__(self):
        #conn = sqlite3.connect('locationsensor.db')
        pass

    def gettable(self, table, cols):
        sql = 'select ' + cols + ' from ' + table
        #print 'selectSQL: ', sql
        return sql
    
    def inserttable(self, table, cols):
        sql = 'insert into ' + table + '( ' + cols + ' ) values( '
        for e in xrange(len(cols.split(','))-1):
            sql += ' ?,'
        sql += ' ?)'
        #print 'insertSQL: ', sql
        return sql

    def copydb(self, fromdb, todb):
        inconn = sqlite3.connect(fromdb)
        inc = inconn.cursor()
        outconn = sqlite3.connect(todb)
        outc = outconn.cursor()
        outconn.execute('delete from loctime')

        inc.execute(self.gettable('loctime', LocDB.loctimecols))
        vals = []
        for row in inc:
            print row
            vals[:] = []
            for idx in xrange(0,len(row)):
                vals.append(row[idx])

            print len(vals), vals
            #outconn.execute("insert into loctime(" + LocDB.loctimecols +") values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", vals)
            outconn.execute(self.inserttable('loctime', LocDB.loctimecols), vals)
            outconn.commit()

        outconn.execute('delete from poi')
        inc.execute(self.gettable('poi', LocDB.poicols))
        for row in inc:
            print row
            vals[:] = []
            for idx in xrange(0,len(row)):
                vals.append(row[idx])

            #outconn.execute("insert into poi(poi,lat, lgt,radius,addr,name,celljsons, wifimac, wificonnmac, btmac, time) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", vals)
            outconn.execute(self.inserttable('poi', LocDB.poicols), vals)
            outconn.commit()
       
    """ import data from tab file generated from
    sqlite3 locationsensor.db 'select Lat,Lgt,StartTime,EndTime,Name,Count,Accuracy,BestAccuracy,Accuname,Poitag,CellJsonValue from loctime' > x
    """
    def txt2db(self, file, db):
        outconn = sqlite3.connect(db)
        outc = outconn.cursor()
        
        with open(file) as f:
            for line in f:
                print line
                self.insert(outconn, line)

    def insert(self, outconn, line):
        vals = line.split('|')
        if float(vals[2]) == 0:
            return

        print vals
        outconn.execute(self.inserttable('loctime', LocDB.loctimecols), vals)
        outconn.commit()

    def close(self):
        self.c.close()

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print 'usage: python importdb from_db to_db'
        #sys.exit(1)

    locdb = LocDB()
    locdb.gettable('loctime', LocDB.loctimecols)
    locdb.gettable('poi', LocDB.poicols)
    locdb.inserttable('loctime', LocDB.loctimecols)
    
    #print 'copying db from %s ===> %s' %(sys.argv[1], sys.argv[2])
    #locdb.txt2db('x.tab')
    #locdb.copydb(sys.argv[1], sys.argv[2])
    locdb.txt2db(sys.argv[1], sys.argv[2])
    
    
