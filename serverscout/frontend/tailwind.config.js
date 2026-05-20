/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: { 50: '#eff6ff', 100: '#dbeafe', 500: '#3b82f6', 600: '#2563eb', 700: '#1d4ed8' },
        severity: { critical: '#ef4444', high: '#f97316', medium: '#eab308', low: '#3b82f6' },
      },
    },
  },
  plugins: [],
}
