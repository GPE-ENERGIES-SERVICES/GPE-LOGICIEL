/**
 * Helpers of the demo data: pseudo-random numbers from a fixed seed, so that
 * the data is the same on every load, and dates relative to today, so that
 * deadlines stay current.
 */

export function seededRandom(seed: number) {
  let state = seed;
  const random = () => {
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const between = (min: number, max: number) => min + Math.floor(random() * (max - min + 1));
  return {
    random,
    between,
    chance: (probability: number) => random() < probability,
    pick: <T>(items: readonly T[]): T => items[Math.floor(random() * items.length)],
    /** Up to count distinct items, in their original order. */
    some: <T>(items: readonly T[], count: number): T[] => {
      const kept = new Set<number>();
      while (kept.size < Math.min(count, items.length)) {
        kept.add(between(0, items.length - 1));
      }
      return items.filter((_, index) => kept.has(index));
    },
    /** An amount rounded to the thousand. */
    amount: (min: number, max: number) => Math.round(between(min, max) / 1000) * 1000,
  };
}

const DAY = 86_400_000;

const today = new Date();
today.setHours(0, 0, 0, 0);

/** A calendar date (yyyy-MM-dd) counted in days from today. */
export function day(offset: number): string {
  return new Date(today.getTime() + offset * DAY).toLocaleDateString("sv-SE");
}

/** An instant on a day counted from today, at a local time. */
export function at(offset: number, hours = 12, minutes = 0): string {
  const date = new Date(today.getTime() + offset * DAY);
  date.setHours(hours, minutes, 0, 0);
  return date.toISOString();
}

/** Days from today to a calendar date. */
export function daysFromToday(date: string): number {
  return Math.round((new Date(`${date}T00:00:00`).getTime() - today.getTime()) / DAY);
}
