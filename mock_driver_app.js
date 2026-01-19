// Mock Driver App for testing real-time location tracking
// This simulates a driver app that sends location updates to the backend

const axios = require('axios');

// Configuration
const BASE_URL = 'http://localhost:8088'; // Default dashboard server port
const DRIVER_IDS = ['1', '2', '3', '4', '5']; // Sample driver IDs

console.log('Starting mock driver location simulator...');

// Function to generate random location near San Francisco
function generateRandomLocation(centerLat = 37.7749, centerLng = -122.4194) {
  // Add random offset (-0.01 to 0.01 degrees ~ within 1km radius)
  const lat = centerLat + (Math.random() - 0.5) * 0.02;
  const lng = centerLng + (Math.random() - 0.5) * 0.02;
  const bearing = Math.floor(Math.random() * 360); // Random bearing 0-359 degrees
  
  return { lat, lng, bearing };
}

// Function to send location update for a driver
async function sendLocationUpdate(driverId) {
  const location = generateRandomLocation();
  
  try {
    const response = await axios.post(`${BASE_URL}/api/drivers/location`, {
      id: driverId,
      lat: location.lat,
      lng: location.lng,
      bearing: location.bearing
    }, {
      headers: {
        'Content-Type': 'application/json'
      }
    });
    
    console.log(`✓ Driver ${driverId}: Location updated - Lat: ${location.lat.toFixed(6)}, Lng: ${location.lng.toFixed(6)}, Bearing: ${location.bearing}°`);
  } catch (error) {
    console.error(`✗ Driver ${driverId}: Failed to update location -`, error.message);
  }
}

// Function to get current driver locations
async function getDriverLocations() {
  try {
    const response = await axios.get(`${BASE_URL}/api/drivers/location`);
    console.log('\nCurrent driver locations:');
    response.data.forEach(driver => {
      console.log(`Driver ${driver.id}: Lat ${driver.lat.toFixed(6)}, Lng ${driver.lng.toFixed(6)}, Bearing ${driver.bearing}°`);
    });
  } catch (error) {
    console.error('Failed to get driver locations:', error.message);
  }
}

// Start simulation
console.log('Sending location updates every 5 seconds...');
setInterval(async () => {
  // Send updates for random drivers
  const randomDriver = DRIVER_IDS[Math.floor(Math.random() * DRIVER_IDS.length)];
  await sendLocationUpdate(randomDriver);
}, 5000);

// Print current locations every 15 seconds
setInterval(async () => {
  await getDriverLocations();
}, 15000);

// Initial location updates
DRIVER_IDS.forEach(async (id, index) => {
  setTimeout(async () => {
    await sendLocationUpdate(id);
  }, index * 1000); // Stagger initial updates
});