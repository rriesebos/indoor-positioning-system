# Indoor positioning system
This repository contains four top-level directories: _measurements-replay_, _plotting_, _positioning-app_, and _positioning-backend_. The _measurements-replay_ directory contains the Java code used to explore different parameter combinations by replaying RSSI measurements. The _plotting_ directory contains all the experimental data, as well as the Python code used to process and plot this data. The _positioning-app_ directory contains the Java code for the Android Application, and the _positioning-backend_ directory contains the code for the Express RESTful API and the databases.

## Setup
To run the back-end, [Docker](https://docs.docker.com/engine/install/) and [Docker Compose](https://docs.docker.com/compose/install/) are required. When Docker Compose is installed, simply run `docker-compose up -d` from the _positioning-backend_ directory to run all containers defined in [docker-compose.yml](positioning-backend/docker-compose.yml). This will start the Express RESTful API, as well as a [Cassandra](https://cassandra.apache.org/_/index.html) database instance and a [MongoDB](https://www.mongodb.com/) database instance.

To run the Android Application, [Android Studio](https://developer.android.com/studio) has to be installed and setup. When Android Studio is installed, simply open the _positioning-app_ directory as a new project, and update `BASE_URL` from [RetrofitClient.java](positioning-app/app/src/main/java/com/rriesebos/positioningapp/api/RetrofitClient.java) to the URL/IP corresponding to your instance of the Express API.

To install the packages used for plotting, run `python -m pip install -r requirements.txt` from the _plotting_ directory.

## Architecture
![Architecture diagram](https://user-images.githubusercontent.com/27828755/129890337-2146636d-685f-472e-9d68-297773d42992.png)

The architecture of the indoor positioning system consists of four main components: the Android application, the Express RESTful API, the Cassandra database and the MongoDB database. These components are shown in the architecture diagram, and elaborated upon in the following subsections.

### Android Application
<p float="center">
  <img src="https://user-images.githubusercontent.com/27828755/129892065-cc4a1589-313b-4a19-9c8c-1c76102446cc.jpg" alt="Beacon list" width="200"/>
  <img src="https://user-images.githubusercontent.com/27828755/129892069-68b81b70-7e8b-4802-ba9b-34e1fa634d0f.jpg" alt="Positioning tab" width="200"/>
  <img src="https://user-images.githubusercontent.com/27828755/129892075-3e89eced-27d4-46f3-9e02-ec6611e986be.jpg" alt="Recording" width="200"/>
  <img src="https://user-images.githubusercontent.com/27828755/129892081-a3798016-b251-4d12-9ba5-ce67968bcc35.jpg" alt="Settings" width="200"/>
</p>


The Android application continuously scans for Bluetooth Low-Energy (BLE) beacons. When beacons are detected, they are displayed in the beacons list. The estimated position of the smartphone is also continuously calculated using the different positioning methods. The results of these calculations are listed in the "Positioning" tab.

The app bar contains a “Start Recording” button in the top right. When this button is pressed the application starts recording the Received Signal Strength Indicator (RSSI) values for the detected beacons along with their timestamps, the calculated distances, the estimated position (using the configurable default positioning method), and an advertising channel estimation. These measurements are send to the Express back-end.

Furthermore, when recording is started, the positioning tab changes to the state in the third image. A "Checkpoint" button is now visible along with a checkpoint counter. Whenever the checkpoint button is pressed the checkpoint counter is incremented, and the last checkpoint timestamp is send to the back-end. Finally, when the user is done recording they can tap the button in the app bar again to stop recording.

All the parameters related to the measurements window, RSSI filtering, distance estimation, positioning and more can be configured in the settings of the application.

### Express RESTful API
The Express back-end server is currently mainly in place to serve as a RESTful API that provides a CRUD interface for the two databases. A short summary of the implemented endpoints is given below.

#### Beacon-related endpoints
Endpoint | Description
------------ | -------------
`GET /beacons` | Retrieve beacon information for all beacons
`GET /beacons/rssi` | Retrieve all beacon measurements
`GET /beacons/{beaconAddress}` | Retrieve beacon information for a specific beacon
`GET /beacons/{beaconAddress}/rssi` | Retrieve beacon measurements for a specific beacon
`POST /beacons` | Create a new beacon
`POST /beacons/{beaconAddress}/rssi` | Create a new beacon measurement
`PUT /beacons/{beaconAddress}` | Update/create a beacon
`DELETE /beacons` | Delete all beacons
`DELETE /beacons/rssi` | Delete all beacon measurements
`DELETE /beacons/{beaconAddress}` | Delete a specific beacon
`DELETE /beacons/{beaconAddress}/rssi` | Delete measurements for a specific beacon

#### Positioning-related endpoints
Endpoint | Description
------------ | -------------
`GET /positioning` | Retrieve all predicted coordinates
`GET /positioning/checkpoints` | Retrieve all checkpoint timestamps
`POST /positioning` | Create new predicted coordinates
`POST /positioning/checkpoints` | Create a new checkpoint timestamp
`DELETE /positioning` | Delete all predicted coordinates
`DELETE /positioning/checkpoints` | Delete all checkpoint timestamps

#### Points of interest endpoints
**NOTE:** These endpoints are implemented, but not used by the Android Application.
Endpoint | Description
------------ | -------------
`GET /points-of-interest` | Retrieve all points of interest
`GET /points-of-interest/{id}` | Retrieve a specific point of interest
`POST /points-of-interest` | Create a new point of interest
`PUT /points-of-interest/{id}` | Update/create a point of interest
`DELETE /points-of-interest` | Delete all points of interest
`DELETE /points-of-interest/{id}` | Delete a specific point of interest

### Databases
There are two databases in which data send to the API is stored. A Cassandra database, and a MongoDB database.

#### Cassandra database
The Cassandra database is used to store time-series data. Specifically, the beacon measurements, predicted coordinates and checkpoint timestamps. These are stored in three separate tables. The schemas are defined in [schema.cql](positioning-backend/cassandra/schema.cql). The beacon measurements are stored in the `measurements_by_beacon` table, which has the following schema:
```
CREATE TABLE IF NOT EXISTS beacons.measurements_by_beacon (
    beacon_address text,
    timeuuid timeuuid,
    rssi int,
    distance double,
    channel int,
    PRIMARY KEY ((beacon_address), timeuuid) )
    WITH CLUSTERING ORDER BY (timeuuid DESC);
```
The predicted coordinates are stored in the `predicted_coordinates` table, with the following schema:
```
CREATE TABLE IF NOT EXISTS positioning.predicted_coordinates (
    timeuuid timeuuid,
    x int,
    y int,
    confidence double,
    PRIMARY KEY (timeuuid)
);
```
As evident from the schema, the confidence indicator is stored together with the coordinates.

Lastly, the checkpoint timestamps are stored in the `checkpoint_timestamps` table, with the following schema:
```
CREATE TABLE IF NOT EXISTS positioning.checkpoint_timestamps (
    timeuuid timeuuid,
    checkpoint int,
    PRIMARY KEY (timeuuid)
);
```

#### MongoDB database
For the final component we have the MongoDB database. The MongoDB database has two so-called collections, one for the beacon information, and one for the points of interest. The beacon information consists of the beacon address (Bluetooth MAC address) serving as a unique identifier, the TX power (transmission power) and the beacon coordinates (x, y), as defined in [beacon.js](positioning-backend/positioning-server/models/beacon.js) by the following Mongoose schema:
```
const BeaconSchema = mongoose.Schema({
    beaconAddress: { type: String, required: true, index: true, unique: true },
    txPower: Number,
    coordinates: {
        x: { type: Number, required: true },
        y: { type: Number, required: true }
    }
});
```

The points of interest are defined in [point-of-interest.js](positioning-backend/positioning-server/models/point-of-interest.js) by a name, description, coordinates and a radius:
```
const PointOfInterestSchema = mongoose.Schema({
    name: { type: String, required: true },
    description: String,
    coordinates: {
        type: {
            x: { type: Number, required: true },
            y: { type: Number, required: true }
        },
        required: true
    },
    radius: { type: Number, required: true }
});
```
