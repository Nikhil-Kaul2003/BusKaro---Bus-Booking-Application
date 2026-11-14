import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import './BusKaroo_OffersCarousel.css';

/**
 * SAMPLE DISPLAY DATA ONLY. Real validity, usage count, caps and eligibility
 * MUST be checked by the Spring Boot backend at checkout.
 * Pass offers={offersFromApi} when a coupon API is available.
 */
export const sampleBusKarooOffers = [
  {
    id: 'first-booking',
    code: 'FIRST25',
    discount: '25%',
    label: 'NEW TRAVELLER',
    title: 'A warm welcome to BusKaroo.',
    description: 'Your first journey deserves a little extra. Save on your first confirmed bus booking.',
    maxSavings: 'Up to ₹250 off',
    details: 'For accounts with no previous completed bookings. Minimum fare ₹499. Valid once per account.',
    theme: 'coral',
  },
  {
    id: 'chhath-special',
    code: 'CHHATH40',
    discount: '40%',
    label: 'FESTIVAL SPECIAL',
    title: 'Celebrate Chhath, travel together.',
    description: 'A special seasonal offer for journeys home during the Chhath festival.',
    maxSavings: 'Up to ₹400 off',
    details: 'Only valid during the festival booking and travel dates configured by the administrator. Minimum fare ₹799. Once per account.',
    theme: 'saffron',
  },
  {
    id: 'monthly-saver',
    code: 'MONTH10',
    discount: '10%',
    label: 'MONTHLY PICK',
    title: 'A little saving, every month.',
    description: 'Make the most of your next trip with our monthly featured reward.',
    maxSavings: 'Up to ₹100 off',
    details: 'Valid during its configured promotional month. Minimum fare ₹399. Once per account for this coupon.',
    theme: 'blue',
  },
  {
    id: 'weekend-escape',
    code: 'WEEKEND10',
    discount: '10%',
    label: 'WEEKEND ESCAPE',
    title: 'Make your weekend go further.',
    description: 'Plans for Saturday or Sunday? Start your getaway with a better fare.',
    maxSavings: 'Up to ₹120 off',
    details: 'For eligible weekend travel dates, as verified at booking. Minimum fare ₹499. Once per account.',
    theme: 'violet',
  },
  {
    id: 'sleeper-special',
    code: 'SLEEP10',
    discount: '10%',
    label: 'SLEEPER SPECIAL',
    title: 'Dream more. Spend less.',
    description: 'Enjoy an overnight journey with savings on an eligible sleeper bus.',
    maxSavings: 'Up to ₹150 off',
    details: 'Only for eligible Sleeper-category buses. Minimum fare ₹599. Once per account.',
    theme: 'teal',
  },
  {
    id: 'welcome-back',
    code: 'BACK10',
    discount: '10%',
    label: 'WELCOME BACK',
    title: 'Good to have you travelling again.',
    description: 'Already enjoyed a BusKaroo journey? Here is something for your next one.',
    maxSavings: 'Up to ₹125 off',
    details: 'For customers with at least one previous completed booking. Minimum fare ₹499. Once per account.',
    theme: 'rose',
  },
];

export default function OffersCarousel({ offers = sampleBusKarooOffers }) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [copiedCode, setCopiedCode] = useState('');
  const [copyError, setCopyError] = useState('');
  const [reduceMotion, setReduceMotion] = useState(false);
  const copyTimerRef = useRef(null);

  const count = offers.length;
  const selected = count ? offers[Math.min(activeIndex, count - 1)] : null;

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined;
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const update = () => setReduceMotion(media.matches);
    update();
    media.addEventListener?.('change', update);
    return () => media.removeEventListener?.('change', update);
  }, []);

  // Keep active index valid if the API later supplies a shorter offer list.
  useEffect(() => {
    if (count > 0 && activeIndex >= count) setActiveIndex(0);
  }, [count, activeIndex]);

  const goTo = useCallback((index) => {
    if (count === 0) return;
    setActiveIndex(((index % count) + count) % count);
    setDetailsOpen(false);
    setCopiedCode('');
    setCopyError('');
  }, [count]);

  useEffect(() => {
    if (count < 2 || hovered || focused || detailsOpen || reduceMotion) return undefined;
    const interval = window.setInterval(() => {
      setActiveIndex((current) => (current + 1) % count);
      setCopiedCode('');
      setCopyError('');
    }, 6000);
    return () => window.clearInterval(interval);
  }, [count, hovered, focused, detailsOpen, reduceMotion]);

  useEffect(() => () => {
    if (copyTimerRef.current) window.clearTimeout(copyTimerRef.current);
  }, []);

  const copyCoupon = async (code) => {
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(code);
      setCopiedCode(code);
      setCopyError('');
      if (copyTimerRef.current) window.clearTimeout(copyTimerRef.current);
      copyTimerRef.current = window.setTimeout(() => setCopiedCode(''), 2500);
    } catch {
      setCopiedCode('');
      setCopyError(`Could not copy automatically. Select and copy: ${code}`);
    }
  };

  if (!selected) return null;

  return (
    <section
      className="bk-offers"
      aria-label="BusKaroo offers and rewards"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocusCapture={() => setFocused(true)}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false);
      }}
      onKeyDown={(event) => {
        if (event.target.closest('input, select, textarea')) return;
        if (event.key === 'ArrowRight') {
          event.preventDefault();
          goTo(activeIndex + 1);
        } else if (event.key === 'ArrowLeft') {
          event.preventDefault();
          goTo(activeIndex - 1);
        }
      }}
    >
      <div className="bk-offers-topline">
        <div className="bk-offers-kicker"><span className="bk-offers-kicker-dot" /> BUSKAROO REWARDS</div>
        <div className="bk-offers-counter" aria-label={`Offer ${activeIndex + 1} of ${count}`}>
          <span>{String(activeIndex + 1).padStart(2, '0')}</span>
          <span className="bk-offers-counter-sep">/</span>
          <span>{String(count).padStart(2, '0')}</span>
        </div>
      </div>

      <div className="bk-offers-viewport">
        <div
          className={`bk-offers-track${reduceMotion ? ' bk-offers-no-motion' : ''}`}
          style={{ transform: `translateX(-${activeIndex * 100}%)` }}
        >
          {offers.map((offer, index) => (
            <div className="bk-offers-slide" key={offer.id || offer.code} aria-hidden={index !== activeIndex}>
              <div className="bk-offers-copy">
                <span className="bk-offers-offer-label">{offer.label}</span>
                <h2>{offer.title}</h2>
                <p>{offer.description}</p>
                <div className="bk-offers-buttons">
                  <Link className="bk-offers-primary-link" to="/search" tabIndex={index === activeIndex ? 0 : -1}>
                    Explore buses <span aria-hidden="true">↗</span>
                  </Link>
                  <button
                    type="button"
                    className="bk-offers-terms-button"
                    onClick={() => setDetailsOpen((open) => !open)}
                    aria-expanded={index === activeIndex ? detailsOpen : false}
                    tabIndex={index === activeIndex ? 0 : -1}
                  >
                    Offer terms <span aria-hidden="true">{index === activeIndex && detailsOpen ? '−' : '+'}</span>
                  </button>
                </div>
              </div>

              <div className={`bk-offers-ticket bk-offers-ticket--${offer.theme || 'coral'}`}>
                <div className="bk-offers-ticket-top">
                  <span>YOUR TRAVEL REWARD</span>
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                    <rect x="3" y="5" width="18" height="14" rx="3" />
                    <path d="M8 5v14M11 9h6M11 13h4" />
                  </svg>
                </div>
                <div className="bk-offers-discount"><strong>{offer.discount}</strong><span>OFF</span></div>
                <p>{offer.maxSavings}</p>
                <div className="bk-offers-perforation" />
                <div className="bk-offers-ticket-bottom">
                  <div className="bk-offers-code-wrap">
                    <small>COUPON CODE</small>
                    <b>{offer.code}</b>
                  </div>
                  <button
                    type="button"
                    className="bk-offers-copy-button"
                    onClick={() => copyCoupon(offer.code)}
                    tabIndex={index === activeIndex ? 0 : -1}
                    aria-label={`Copy coupon code ${offer.code}`}
                  >
                    {copiedCode === offer.code ? 'Copied ✓' : 'Copy code'}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {detailsOpen && (
        <div className="bk-offers-terms" role="note">
          <strong>{selected.code}:</strong> {selected.details}
        </div>
      )}
      {copyError && <div className="bk-offers-copy-error" role="alert">{copyError}</div>}

      <div className="bk-offers-footer">
        <span className="bk-offers-footnote">One redemption per user per coupon · Terms apply</span>
        <div className="bk-offers-controls" aria-label="Offer navigation">
          <div className="bk-offers-dots">
            {offers.map((offer, index) => (
              <button
                key={offer.id || offer.code}
                type="button"
                className={`bk-offers-dot${index === activeIndex ? ' is-active' : ''}`}
                aria-label={`Show ${offer.label.toLowerCase()} offer`}
                aria-current={index === activeIndex ? 'true' : undefined}
                onClick={() => goTo(index)}
              />
            ))}
          </div>
          <button className="bk-offers-arrow" type="button" aria-label="Previous offer" onClick={() => goTo(activeIndex - 1)}>←</button>
          <button className="bk-offers-arrow" type="button" aria-label="Next offer" onClick={() => goTo(activeIndex + 1)}>→</button>
        </div>
      </div>
    </section>
  );
}
