import os
import csv
from datetime import datetime

class Experiment:
    def __init__(self, file_name="visa_results.csv", output_dir="./outputs", experiment_name="temp_experiment", model_name="gemma3:4b"):
        self.file_path = os.path.join(output_dir, file_name)
        os.makedirs(output_dir, exist_ok=True)

        self.experiment_name = experiment_name
        self.model_name = model_name

    """
    Write summarized metrics to CSV.

    Example output row:
        epoch, phase, train_loss, train_acc, val_loss, val_acc
    """
    def write_csv(self, latency, user_goal=None, raw_response=None, action=None, target_text=None, input_text=None, success=""):
        base_row = {
            "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M"),
            "experiment": self.experiment_name,
            "model": self.model_name,
            "latency": latency
        }

        if user_goal is not None: base_row.update({"userGoal": user_goal})
        if user_goal is not None: base_row.update({"rawResponse": raw_response})
        if action is not None: base_row.update({"action": action})
        if target_text is not None: base_row.update({"targetText": target_text})
        if input_text is not None: base_row.update({"inputText": input_text})
        base_row.update({"success": success}) # will be manually added

        self._append_csv(base_row)

        return base_row

    def _append_csv(self, row):
        csv_exists = os.path.exists(self.file_path)
        csv_empty = (not csv_exists) or os.path.getsize(self.file_path) == 0

        with open(self.file_path, "a", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=row.keys())
            if csv_empty:
                writer.writeheader()
            writer.writerow(row)

    """
    # in case will add more columns
        def _append_csv(self, row):
        csv_exists = os.path.exists(self.file_path)
        
        csv_empty = (not csv_exists) or os.path.getsize(self.file_path) == 0

        # if CSV already exists and new columns appear, rewrite safely
        if csv_exists and not csv_empty:
            old_df = pd.read_csv(self.file_path)
            new_df = pd.DataFrame([row])
            combined = pd.concat([old_df, new_df], ignore_index=True)
            combined.to_csv(self.file_path, index=False)
        else: # create file and write values
            with open(self.file_path, "w", newline="", encoding="utf-8") as f:
                writer = csv.DictWriter(f, fieldnames=row.keys())
                writer.writeheader()
                writer.writerow(row)
    """

