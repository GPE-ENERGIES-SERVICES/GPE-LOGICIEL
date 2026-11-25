import { CCTP_EXAMPLE, RC_CCAP_EXAMPLE } from "@/mock/specialAnalyses";
import type { CctpAnalysis, RcCcapAnalysis } from "@/types/specialAnalysis";

// shortcut: example data while the server does not read the RC, the CCAP and the CCTP; call the API once it does.
export function getRcCcapAnalysis(tenderId: number): Promise<RcCcapAnalysis> {
  void tenderId;
  return Promise.resolve(RC_CCAP_EXAMPLE);
}

export function getCctpAnalysis(tenderId: number): Promise<CctpAnalysis> {
  void tenderId;
  return Promise.resolve(CCTP_EXAMPLE);
}
