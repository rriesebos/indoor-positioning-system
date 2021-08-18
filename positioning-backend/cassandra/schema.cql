CREATE KEYSPACE IF NOT EXISTS beacons WITH replication = 
    {'class': 'SimpleStrategy', 'replication_factor' : 1};

CREATE TABLE IF NOT EXISTS beacons.measurements_by_beacon (
    beacon_address text,
    timeuuid timeuuid,
    rssi int,
    distance double,
    channel int,
    PRIMARY KEY ((beacon_address), timeuuid) )
    WITH CLUSTERING ORDER BY (timeuuid DESC);


CREATE KEYSPACE IF NOT EXISTS positioning WITH replication = 
    {'class': 'SimpleStrategy', 'replication_factor' : 1};

CREATE TABLE IF NOT EXISTS positioning.predicted_coordinates (
    timeuuid timeuuid,
    x int,
    y int,
    confidence double,
    PRIMARY KEY (timeuuid)
);

CREATE TABLE IF NOT EXISTS positioning.checkpoint_timestamps (
    timeuuid timeuuid,
    checkpoint int,
    PRIMARY KEY (timeuuid)
);