# CyberCando Transport Bot

**The Future of Automated Transport Dispatch**

This project is a modern transport dispatch bot for Telegram, designed to handle ride requests for Taxis 🚕, Motorbikes 🏍️, Tricycles 🛺, and Delivery 🚚.

## Features
- **Multi-Vehicle Support**: Request different types of transport.
- **Automated Dispatch**: Simulates driver matching and dispatching.
- **Telegram Interface**: Easy-to-use chat interface for users.
- **Mock Pricing Engine**: Simulates fare calculation without external dependencies.
- **Real-time Driver Tracking**: Track driver locations with live map visualization.

## Architecture
- **MVC Pattern**: Clear separation of concerns.
- **Java**: Built with robustness in mind.
- **Extensible**: Ready to connect with real databases and payment gateways.

## Getting Started
1. Clone the repository.
2. Set up your Telegram Bot Token in `.env`.
3. Run the application.

## Roadmap
- [x] Core Bot Logic
- [ ] Database Integration (SQLite/PostgreSQL)
- [x] Admin Dashboard (with real-time driver tracking)
- [x] Real-time Driver Tracking

## Real-time Driver Tracking System

The system includes:
- **Java Backend**: Handles driver location updates via HTTP API
- **WebSocket Integration**: Real-time updates to dashboard
- **Interactive Map**: Visualize driver locations with Leaflet
- **Dashboard UI**: Shows driver locations and status in real-time

Developed for CyberCando.
