import type { StaticImageData } from "next/image";

import logo from "@/assets/logo-gpe.png";

/** The name of the software, the same for everyone. */
export const PRODUCT_NAME = "الخشخاش";

/**
 * Who the application is presented for. Only one company for now.
 * shortcut: a constant, built with the application; read from the address when it serves several companies.
 */
export const BRAND: { name: string; logo: StaticImageData } = {
  name: "GPE Énergies & Services",
  logo,
};
