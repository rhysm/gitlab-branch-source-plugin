package argelbargel.jenkins.plugins.gitlab_branch_source;


import argelbargel.jenkins.plugins.gitlab_branch_source.actions.GitLabSCMAcceptMergeRequestAction;
import argelbargel.jenkins.plugins.gitlab_branch_source.actions.GitLabSCMCauseAction;
import argelbargel.jenkins.plugins.gitlab_branch_source.actions.GitLabSCMHeadMetadataAction;
import argelbargel.jenkins.plugins.gitlab_branch_source.actions.GitLabSCMPublishAction;
import argelbargel.jenkins.plugins.gitlab_branch_source.heads.GitLabSCMMergeRequestHead;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty;

import javax.annotation.Nonnull;

import static hudson.model.Result.SUCCESS;


@SuppressWarnings("unused")
@Extension
public class GitLabSCMRunListener extends RunListener<Run<?, ?>> {
    @Override
    public void onStarted(Run<?, ?> build, TaskListener listener) {
        GitLabSCMHeadMetadataAction metadata = getMetadataAction(build);
        GitLabSCMPublishAction publishAction = build.getParent().getAction(GitLabSCMPublishAction.class);
        if (metadata != null && publishAction != null) {
            GitLabSCMCauseAction cause = build.getAction(GitLabSCMCauseAction.class);
            String description = (cause != null) ? cause.getDescription() : "";
            publishAction.updateBuildDescription(build, description, listener);
            publishAction.publishStarted(build, metadata, description);
        }
    }

    @Override
    public void onCompleted(Run<?, ?> build, @Nonnull TaskListener listener) {
        GitLabSCMHeadMetadataAction metadata = getMetadataAction(build);
        GitLabSCMPublishAction publishAction = build.getParent().getAction(GitLabSCMPublishAction.class);
        if (metadata != null && publishAction != null) {
            publishAction.publishResult(build, metadata);
        }

        if (build.getResult() == SUCCESS) {
            GitLabSCMAcceptMergeRequestAction acceptAction = build.getParent().getAction(GitLabSCMAcceptMergeRequestAction.class);
            if (acceptAction != null) {
                acceptAction.acceptMergeRequest(build, listener);
            }
        }
    }

    private GitLabSCMHeadMetadataAction getMetadataAction(Run<?, ?> build) {
        GitLabSCMHeadMetadataAction metadata = build.getAction(GitLabSCMHeadMetadataAction.class);
        if (metadata == null) {
            metadata = build.getParent().getAction(GitLabSCMHeadMetadataAction.class);
        }
        /* If no metadata can be found attempt to put together enough information so that build statuses can be
         * reported back to GitLab even on manually triggered builds in Jenkins */
        if (metadata == null && build instanceof WorkflowRun) {
            WorkflowRun workflowRun = (WorkflowRun) build;
            BranchJobProperty branchJobProperty = workflowRun.getParent().getProperty(BranchJobProperty.class);
            if (branchJobProperty.getBranch().getHead() instanceof GitLabSCMMergeRequestHead) {
                GitLabSCMMergeRequestHead gitlabMergeRequestHead = (GitLabSCMMergeRequestHead) branchJobProperty.getBranch().getHead();
                String objectDisplayName = branchJobProperty.getBranch().getHead().getName();
                String branchName = gitlabMergeRequestHead.getSource().getName();
                String hash = gitlabMergeRequestHead.getSource().getRevision().getHash();
                int projectId = gitlabMergeRequestHead.getSource().getProjectId();
                //The URL doesn't seem to be used to report back build status
                String url = "";
                metadata = new GitLabSCMHeadMetadataAction(objectDisplayName, projectId, branchName, hash, url);
            }
        }

        return metadata;
    }
}
