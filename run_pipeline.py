#!/usr/bin/env python3
import os
import sys
import subprocess
import argparse
import signal

CURRENT_SUBPROCESS = None
DEFAULT_SOLVER_ENGINE = "ACE-2.6.jar"
INSTANCE_RANGE = [f"Instance{i}" for i in range(1, 25)]

def termination_signal_handler(signum, frame):
    global CURRENT_SUBPROCESS
    print("\n\n🛑 [PROCESS TERMINATION INTERCEPTED] Cleaning up active server assets...")
    if CURRENT_SUBPROCESS and CURRENT_SUBPROCESS.poll() is None:
        print(f" -> Actively killing orphaned subprocess (PID: {CURRENT_SUBPROCESS.pid})...")
        CURRENT_SUBPROCESS.terminate()
        try:
            CURRENT_SUBPROCESS.wait(timeout=5)
            print(" -> Subprocess killed successfully.")
        except subprocess.TimeoutExpired:
            print(" -> Subprocess stalled. Forcing immediate shutdown...")
            CURRENT_SUBPROCESS.kill()
    print("❌ Orchestrator terminated safely. Server space cleared.\n")
    sys.exit(128 + signum)

signal.signal(signal.SIGINT, termination_signal_handler)
signal.signal(signal.SIGTERM, termination_signal_handler)

def run_command(cmd, log_path=None, dry_run=False):
    global CURRENT_SUBPROCESS
    if dry_run:
        if log_path:
            print(f" [DRY-RUN LOG REDIRECT] Out: {log_path}")
        print(f" [DRY-RUN CMD] {' '.join(cmd)}")
        return True

    print(f"Executing: {' '.join(cmd)}")
    try:
        if log_path:
            with open(log_path, "w") as log_file:
                CURRENT_SUBPROCESS = subprocess.Popen(cmd, stdout=log_file, stderr=subprocess.PIPE, text=True)
                stdout, stderr = CURRENT_SUBPROCESS.communicate()
        else:
            CURRENT_SUBPROCESS = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            stdout, stderr = CURRENT_SUBPROCESS.communicate()
            
        if CURRENT_SUBPROCESS.returncode != 0:
            print(f"\n❌ [CRITICAL CRASH] Command failed with exit code {CURRENT_SUBPROCESS.returncode}")
            print(f"Error Log:\n{stderr}")
            return False
        return True
    except Exception as e:
        print(f"\n❌ [CRITICAL CRASH] Process pipeline exception occurred: {e}")
        return False

def execute_pipeline(instance, scenario, mode, is_sandbox, model_type, solver_engine, solver_args, dry_run):
    print(f"\n🚀 Pipeline Engaged: [{instance}] | Scenario: [{str(scenario).upper()}] | Model: [{model_type.upper()}]")
    
    mapping = {
        "standard": ("nurse_rostering.py", "nurse_rostering"),
        "standard_rob": ("nurse_rostering_rob.py", "nurse_rostering_rob"),
        "dual": ("nurse_rostering_dual.py", "nurse_rostering_dual"),
        "dual_rob": ("nurse_rostering_dual_rob.py", "nurse_rostering_dual_rob")
    }
    script_name, prefix_name = mapping[model_type]
    native_pycsp_filename = f"{prefix_name}-{scenario}-{instance}.xml"

    # CRITICAL EXTENSION: Embed model paradigm directly into logs/sols for total traceability
    file_signature = f"{instance}_{model_type}_{scenario}"

    if is_sandbox:
        json_input = f"exp-001/json_inputs/{instance}.json"
        profile_dir = "exp-001/robust_profiles"
        log_dir = "exp-001/solver_logs"
        xml_out_dir = "exp-001/solution_xmls"
        profile_file = os.path.join(profile_dir, f"{instance}_robust_{scenario}.json")
        compiled_xml = native_pycsp_filename 
        official_xml_output = os.path.join(xml_out_dir, f"{file_signature}_solution.xml")
    else:
        json_input = f"json/{instance}.json"
        profile_dir = f"robust_profiles_{scenario}"
        
        # Explicit path parsing fix mapping cleanly to your empty folders
        if model_type == "standard":
            root_repo = "xmls"
        elif model_type == "standard_rob":
            root_repo = "xmls_rob"
        elif model_type == "dual":
            root_repo = "xmls_dual"
        else:
            root_repo = "xmls_dual_rob"

        log_dir = os.path.join(root_repo, "solver_logs")
        xml_out_dir = os.path.join(root_repo, "solution_xmls")
        
        profile_file = os.path.join(profile_dir, f"{instance}_robust_{scenario}.json")
        compiled_xml = os.path.join(root_repo, native_pycsp_filename)
        official_xml_output = os.path.join(xml_out_dir, f"{file_signature}_official_sol.xml")

    solver_log_output = os.path.join(log_dir, f"{file_signature}_ace.log")
    
    if not dry_run:
        os.makedirs(profile_dir, exist_ok=True)
        os.makedirs(log_dir, exist_ok=True)
        os.makedirs(xml_out_dir, exist_ok=True)

    if "rob" in model_type and mode in ["profile", "all"]:
        print("\n--- Phase 1: Dynamic Robust Profile Generation ---")
        cmd = [
            "python3", "robust_profile_generator.py", scenario,
            f"-data={json_input}", f"-output={profile_dir}/"
        ]
        if not run_command(cmd, dry_run=dry_run): return False

    if mode in ["compile", "all"]:
        print("\n--- Phase 2: Compiling Model (Variant Synchronized) ---")
        if not dry_run and "rob" in model_type and mode == "compile" and not os.path.exists(profile_file):
            print(f"❌ [Safety Intercept] Compilation halted. Missing robust dependency: {profile_file}")
            return False
            
        cmd = [
            "python3", script_name,
            f"-data={json_input}",
            f"-variant={scenario}",
            "-export"
        ]
        if "rob" in model_type:
            cmd.append(f"-robust={profile_file}")
            
        if not run_command(cmd, dry_run=dry_run): return False
        if not dry_run and not is_sandbox and os.path.exists(native_pycsp_filename):
            os.rename(native_pycsp_filename, compiled_xml)

    if mode in ["solve", "all"]:
        print("\n--- Phase 3: Invoking Java Solver Instance ---")
        current_xml_target = native_pycsp_filename if is_sandbox else compiled_xml
        if not dry_run and not os.path.exists(current_xml_target):
            print(f"❌ [Error] Target XML problem instance missing: {current_xml_target}. Skipping solve.")
            return False
        cmd = ["java", "-jar", solver_engine, current_xml_target]
        if solver_args:
            cmd.extend(solver_args.strip().split())
        if not run_command(cmd, log_path=solver_log_output, dry_run=dry_run): return False

    if mode in ["solve", "all"]:
        print("\n--- Phase 4: Parsing Solver Logs to Official Roster XML ---")
        if not dry_run and not os.path.exists(solver_log_output):
            print(f"❌ [Error] Solver log tracking file missing: {solver_log_output}. Skipping parse.")
            return False
        cmd = [
            "python3", "log_to_xml.py",
            json_input,
            solver_log_output,
            official_xml_output
        ]
        if not run_command(cmd, dry_run=dry_run): return False
        
    print(f"✅ Target Pipeline Process Checked: {instance}")
    return True

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Unified Multi-Paradigm PhD Experiment Pipeline Orchestrator")
    parser.add_argument("--mode", choices=["profile", "compile", "solve", "all"], default="all")
    parser.add_argument("--instance", default="Instance10")
    parser.add_argument("--scenario", choices=["baseline", "surge", "crisis"], default="baseline")
    parser.add_argument("--model", choices=["standard", "standard_rob", "dual", "dual_rob"], required=True)
    parser.add_argument("--sandbox", action="store_true")
    parser.add_argument("--solver-bin", default=DEFAULT_SOLVER_ENGINE)
    parser.add_argument("--solver-args", default="")
    parser.add_argument("--dry-run", action="store_true")
    
    args = parser.parse_args()
    targets = INSTANCE_RANGE if args.instance.lower() == "all" else [args.instance]
    
    print(f"=========================================================================")
    print(f"   ORCHESTRATOR UNIFIED PARADIGM RUNNER | Mode: {args.mode.upper()}")
    print(f"=========================================================================")
    
    failures = []
    for t_inst in targets:
        if not execute_pipeline(t_inst, args.scenario, args.mode, args.sandbox, args.model, args.solver_bin, args.solver_args, args.dry_run):
            failures.append(t_inst)
            print(f"⚠️ [Execution Interrupted] Pipeline fault encountered on {t_inst}. Advancing...")
            
    print(f"\n=========================================================================")
    print(f"   QUEUE EXECUTION COMPLETED")
    print(f"=========================================================================")
    if failures:
        print(f"❌ Failed components: {failures}")
    else:
        print("🎉 Matrix check completed successfully with zero pipeline omissions.")
