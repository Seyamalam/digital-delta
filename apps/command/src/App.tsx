"use client";
import { OperationsProvider, type OperationsOptions } from "./operations/OperationsProvider";
import { OverviewPage } from "./operations/OverviewPage";
export function App(options: OperationsOptions = {}) {
  return <OperationsProvider {...options}><OverviewPage /></OperationsProvider>;
}
export { scenarioReducer } from "./operations/scenario";
