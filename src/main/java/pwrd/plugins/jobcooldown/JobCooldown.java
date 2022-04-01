/*
 * The MIT License
 * 
 * Copyright (c) 2009-2011, Ushus Technologies LTD., Shinod K Mohandas
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pwrd.plugins.jobcooldown;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.*;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import pwrd.plugins.postbuildtask.LogProperties;
import pwrd.plugins.postbuildtask.PostbuildTask;
import pwrd.plugins.postbuildtask.ResumeScriptProperties;
import pwrd.plugins.postbuildtask.TaskProperties;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;



public class JobCooldown extends Builder implements SimpleBuildStep {



	@DataBoundConstructor
	public JobCooldown(Boolean coolDownOpen,Long cooldownTime,String cooldownScript) {
		this.coolDownOpen=coolDownOpen;
		this.cooldownTime=cooldownTime;
		this.cooldownScript=cooldownScript;
	}


	private static  long DEFAULTE_COOL_TIME = 60 *60 *2;
	private static  int MAX_RECURSION_DEEP = 20;
	private Long cooldownTime;

	private Boolean coolDownOpen;

	private String cooldownScript;


	public Long getCooldownTime() {
		return cooldownTime;
	}

	@DataBoundSetter
	public void setCooldownTime(Long cooldownTime) {
		this.cooldownTime = cooldownTime;
	}


	public Boolean getCoolDownOpen() {
		return coolDownOpen;
	}
	@DataBoundSetter
	public void setCoolDownopen(Boolean coolDownOpen) {
		this.coolDownOpen = coolDownOpen;
	}

	public String getCooldownScript() {
		return cooldownScript;
	}

	@DataBoundSetter
	public void setCooldownScript(String cooldownScript) {
		this.cooldownScript = cooldownScript;
	}

	public JobCooldown() {
	}

	private Run<?, ?> getNearestSuccessBuild(Run<?, ?> build){
		int i = 0;
		Run<?, ?>  itar = build;

		while(null != (itar = itar.getPreviousBuild()) && (i++ < MAX_RECURSION_DEEP)) {
			if (itar.getResult() != Result.SUCCESS){
				continue;
			}
			return itar;
		}

		/* 没有找到直接返回null */
		return null;
	}

	/**
	 * This method will return the command intercepter as per the node OS
	 *
	 * @param launcher
	 * @param script
	 * @return CommandInterpreter
	 */
	private CommandInterpreter getCommandInterpreter(Launcher launcher,
													 String script, long remain) {
		if (launcher.isUnix()) {
			script = ("remainCoolTime="+remain + ";\n")+ script;
			return new Shell(script);
		}
		else {
			script = ("set remainCoolTime="+remain + ";\n") + script;
			return new BatchFile(script);
		}

	}


	@Override
	public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher,
						TaskListener listener) throws InterruptedException, IOException {

		listener.getLogger().println("[JobCooldown] [start]");
		listener.getLogger().println("[JobCooldown] coolDownOpen=" +coolDownOpen);
		listener.getLogger().println("[JobCooldown] cooldownTime=" +cooldownTime);
		listener.getLogger().println("[JobCooldown] cooldownScript=" +cooldownScript);

		if (coolDownOpen){
			Run<?, ?> nearestSuccessBuild = getNearestSuccessBuild(build);
			if (null != nearestSuccessBuild){
				long prevTime = nearestSuccessBuild.getStartTimeInMillis()/1000;
				long now = System.currentTimeMillis()/1000;
				long cooldown = cooldownTime;
				if (null == cooldownTime || 0 == cooldownTime){
					cooldown = DEFAULTE_COOL_TIME;
				}
				listener.getLogger().println("[JobCooldown] nearestSuccessBuild ="+ nearestSuccessBuild.getNumber()
						+",curTime=" +now +", prevTime=" + prevTime +", sub=" +(now - prevTime)+ ", cooldown="+cooldown);
				if ( (prevTime + cooldown) > now){
					listener.getLogger().println("[JobCooldown] cur job cooling, stop job");
					build.setResult(Result.ABORTED);

					listener.getLogger().println("[JobCooldown] Running script  : " + cooldownScript);
					CommandInterpreter runner = getCommandInterpreter(launcher,
							cooldownScript, (prevTime + cooldown) - now );
					Result result = runner.perform((AbstractBuild)build, launcher, listener) ? Result.SUCCESS
							: Result.FAILURE;
					listener.getLogger().println(
							"[JobCooldown] Running script result : " + result.toString());

					build.getExecutor().doStop();
				}
			}
		}

		listener.getLogger().println("[JobCooldown] [end]");
	}


	//@Symbol("greet")
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {



		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Job Cooldown";
		}

		@Override
		public String getHelpFile() {
			return "/plugin/postbuild-task/help/main.html";
		}



		public FormValidation doCheckName(@QueryParameter String value, @QueryParameter boolean useFrench)
				throws IOException, ServletException {
			if (value.length() == 0) {
				return FormValidation.error("value.length() == 0");
			}
			if (value.length() < 4) {
				return FormValidation.warning("value.length() < 4");
			}
			if (!useFrench && value.matches(".*[éáàç].*")) {
				return FormValidation.warning("else");
			}
			return FormValidation.ok();
		}


	}

}
