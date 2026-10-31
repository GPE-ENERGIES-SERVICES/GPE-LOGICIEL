"use client";

import { useParams } from "next/navigation";

import { EstimateEditor } from "@/components/estimate/EstimateEditor";

export default function EstimatePage() {
  const { id } = useParams<{ id: string }>();
  return <EstimateEditor key={id} id={Number(id)} />;
}
