"use client";

import { useParams } from "next/navigation";

import { MemoirWorkspaceView } from "@/components/memoir/MemoirWorkspace";

export default function MemoirPage() {
  const { id } = useParams<{ id: string }>();
  return <MemoirWorkspaceView key={id} id={Number(id)} />;
}
