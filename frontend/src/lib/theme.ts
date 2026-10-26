/** Key of the chosen theme in the browser ("light" or "dark"; absent: the theme of the system). */
export const THEME_STORAGE_KEY = "gpe-theme";

/**
 * Applies the chosen theme before the first paint, so that a page never
 * flashes in the other theme. Run inline in the head of every page.
 */
export const THEME_SCRIPT = `try{var t=localStorage.getItem("${THEME_STORAGE_KEY}");if(t==="light"||t==="dark")document.documentElement.dataset.theme=t}catch(e){}`;
