// Responsive utility functions and breakpoints

export const breakpoints = {
  mobile: '480px',
  tablet: '768px',
  desktop: '1024px',
  wide: '1440px',
};

// Check if device is mobile
export const isMobile = () => {
  return window.innerWidth <= 768;
};

// Get responsive value based on screen size
export const getResponsiveValue = <T,>(mobile: T, tablet: T, desktop: T): T => {
  const width = window.innerWidth;
  if (width <= 480) return mobile;
  if (width <= 768) return tablet;
  return desktop;
};

// Responsive padding
export const responsivePadding = {
  mobile: '15px',
  tablet: '25px',
  desktop: '40px',
};

// Responsive font sizes
export const responsiveFontSize = {
  small: {
    mobile: '12px',
    tablet: '13px',
    desktop: '14px',
  },
  medium: {
    mobile: '14px',
    tablet: '15px',
    desktop: '16px',
  },
  large: {
    mobile: '18px',
    tablet: '20px',
    desktop: '24px',
  },
  xlarge: {
    mobile: '24px',
    tablet: '28px',
    desktop: '32px',
  },
};

// Media query helper
export const mediaQuery = {
  mobile: `@media (max-width: ${breakpoints.mobile})`,
  tablet: `@media (max-width: ${breakpoints.tablet})`,
  desktop: `@media (min-width: ${breakpoints.desktop})`,
};
