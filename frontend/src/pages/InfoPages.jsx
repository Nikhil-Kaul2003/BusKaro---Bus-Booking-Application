import React from "react";
import { Link } from "react-router-dom";

const styles = {
  page: {
    minHeight: "100vh",
    background: "#0b0f19",
    color: "#cbd5e1",
    padding: "64px 24px",
    fontFamily: "system-ui, sans-serif",
  },
  box: { maxWidth: 720, margin: "0 auto" },
  back: { color: "#f97316", textDecoration: "none", fontSize: 14 },
  title: { color: "#fff", fontSize: 32, margin: "16px 0 8px" },
  line: { width: 40, height: 2, background: "#f97316", marginBottom: 24 },
  heading: { color: "#fff", fontSize: 18, margin: "24px 0 8px" },
  text: { lineHeight: 1.7, fontSize: 15, margin: 0 },
};

function Page({ title, children }) {
  return (
    <div style={styles.page}>
      <div style={styles.box}>
        <Link to="/" style={styles.back}>← Home</Link>
        <h1 style={styles.title}>{title}</h1>
        <div style={styles.line} />
        {children}
      </div>
    </div>
  );
}

const Section = ({ title, children }) => (
  <>
    <h2 style={styles.heading}>{title}</h2>
    <p style={styles.text}>{children}</p>
  </>
);

export function HelpCenter() {
  return (
    <Page title="Help Center">
      <Section title="Booking">
        Search a trip, pick your seats, add passenger details and pay.
      </Section>
      <Section title="Cancellation">
        Cancel from My Bookings, up to 15 minutes before departure.
      </Section>
      <Section title="Pending payments">
        Unpaid bookings expire automatically after 15 minutes.
      </Section>
    </Page>
  );
}

export function ContactUs() {
  return (
    <Page title="Contact Us">
      <Section title="Email">support@buskaroo.com</Section>
      <Section title="Phone">+91 00000 00000</Section>
      <Section title="Hours">Daily, 9 AM – 9 PM IST</Section>
    </Page>
  );
}

export function TermsOfService() {
  return (
    <Page title="Terms of Service">
      <Section title="Bookings">
        Tickets are valid only for the booked trip, seats and passengers.
      </Section>
      <Section title="Cancellations">
        Allowed until 15 minutes before departure.
      </Section>
      <Section title="Conduct">
        Passengers must carry valid ID and follow crew instructions.
      </Section>
    </Page>
  );
}

export function PrivacyPolicy() {
  return (
    <Page title="Privacy Policy">
      <Section title="What we collect">
        Your account details and passenger names for bookings.
      </Section>
      <Section title="How we use it">
        Only to process bookings and support you. We never sell your data.
      </Section>
      <Section title="Your rights">
        Contact us to view or delete your data.
      </Section>
    </Page>
  );
}