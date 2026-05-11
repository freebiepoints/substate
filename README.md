# SubState 💳

SubState is a modern Android application designed to help users track, manage, and optimize their recurring subscriptions. Built with Kotlin and Firebase, it provides a real-time dashboard of financial commitments and proactive reminders for upcoming renewals.

## 🚀 Features

- **Financial Dashboard**: Instant visibility into total monthly and annual subscription expenses.
- **Real-time Sync**: Powered by Firebase Firestore, ensuring data is always up-to-date across sessions.
- **Smart Reminders**: Integrated `WorkManager` checks for upcoming renewals in the background and sends system notifications.
- **Advanced Filtering & Sorting**: Organize subscriptions by name, cost, or due date, and filter by categories like Entertainment, Software, and Health.
- **Intelligent Date Management**: Automatically advances subscription due dates to the next billing cycle (Weekly, Monthly, or Annually) once a payment date passes.
- **Secure Authentication**: Robust user sign-up and login using Firebase Auth, featuring real-time password complexity validation.

## 🛠 Tech Stack

- **Language**: Kotlin 2.0+
- **Architecture**: Activity-based with separation of concerns via Utility objects and Workers.
- **Backend**: 
  - Firebase Authentication (User management)
  - Firebase Firestore (Real-time NoSQL database)
- **Android Jetpack**:
  - `WorkManager` (Background tasks)
  - `ConstraintLayout` (Responsive UI)
  - `Material Components` (Modern design)
  - `ViewBinding/DataBinding` (Optional/Context dependent)

## 📦 Setup & Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/freebiepoints/SubState.git
   ```
2. **Firebase Configuration**:
   - Create a project in the [Firebase Console](https://console.firebase.google.com/).
   - Add an Android app with the package name `com.example.substate`.
   - Download the `google-services.json` and place it in the `app/` directory.
   - Enable **Email/Password** authentication in the Firebase Auth tab.
   - Create a **Firestore Database** in test mode or with appropriate security rules.
3. **Build**:
   - Open the project in Android Studio (Ladybug or newer recommended).
   - Sync Gradle and run the `:app` module.
